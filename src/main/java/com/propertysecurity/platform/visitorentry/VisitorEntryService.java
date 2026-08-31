package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.audit.AuditAction;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.invitation.Invitation;
import com.propertysecurity.platform.invitation.InvitationRepository;
import com.propertysecurity.platform.invitation.InvitationStatus;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import com.propertysecurity.platform.propertysupervisor.PropertySupervisorRepository;
import com.propertysecurity.platform.resident.Resident;
import com.propertysecurity.platform.resident.ResidentRepository;
import com.propertysecurity.platform.unit.PropertyUnit;
import com.propertysecurity.platform.unit.PropertyUnitRepository;
import com.propertysecurity.platform.vehicle.Vehicle;
import com.propertysecurity.platform.vehicle.VehicleService;
import com.propertysecurity.platform.visitorentry.dto.VisitorHistoryEntryResponse;
import com.propertysecurity.platform.visitorentry.dto.WalkInVisitorRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class VisitorEntryService {

    private final VisitorEntryRepository visitorEntryRepository;
    private final InvitationRepository invitationRepository;
    private final GuardRepository guardRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyManagerRepository propertyManagerRepository;
    private final PropertySupervisorRepository propertySupervisorRepository;
    private final ResidentRepository residentRepository;
    private final PropertyUnitRepository propertyUnitRepository;
    private final VehicleService vehicleService;
    private final AuditLogService auditLogService;
    private final ShortCodeLookupAttemptRepository shortCodeLookupAttemptRepository;
    private final ShortCodeRateLimiter shortCodeRateLimiter;

    /**
     * visitingResidentName/recognizedVehicleOwnerName are plain Strings, not
     * navigable entities: extracted while checkIn()'s transaction is still
     * open, so whoever builds the response from this later (after the
     * transaction has closed) can't trip a LazyInitializationException on
     * them — see the comment on VisitorHistoryEntryResponse for why that
     * hazard gets taken seriously here.
     */
    public record CheckInResult(
            VisitorEntry entry,
            boolean vehicleRecognized,
            String visitingResidentName,
            String recognizedVehicleOwnerName) {
    }

    /**
     * Validates the scanned token (status + time window), confirms the
     * scanning guard is posted at the same property the invitation is for,
     * marks the invitation USED, and creates the visitor_entry row — all in
     * one transaction, with an audit_log row for each of the two writes
     * (CLAUDE.md rule 2). If a vehicle registration is captured, finds-or-
     * creates that vehicle (audited separately — vehicle is also named in
     * CLAUDE.md rule 2) and flags whether it's recognized as belonging to a
     * resident.
     */
    public CheckInResult checkIn(Long guardUserId, String qrToken, String vehicleRegistration, LocalDateTime clientClaimedAt) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        Invitation invitation = invitationRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid check-in code"));

        validateStatus(invitation);
        validateTimeWindow(invitation);
        validateSameProperty(guard, invitation);

        return performCheckIn(guard, invitation, guardUserId, vehicleRegistration, clientClaimedAt);
    }

    /**
     * Same outcome as checkIn(), reached by typing a 6-digit short code
     * instead of scanning a QR. Looked up scoped to the guard's own
     * property (see InvitationRepository), so this never needs
     * validateSameProperty — a match by construction can't be for another
     * property. Rejections classify into a stable {@link CheckInRejectionReason}
     * instead of throwing the same generic BadRequestException validateStatus/
     * validateTimeWindow do for qrToken, because the frontend needs to route
     * each outcome differently (walk-in vs. re-type) without parsing prose —
     * see classifyCodeError's TODO in GatePage.tsx.
     *
     * Failed lookups are rate-limited per guard and written to
     * short_code_lookup_attempt (not audit_log — see that table's own
     * comment for why) so a token being hammered is visible.
     */
    public CheckInResult checkInByShortCode(Long guardUserId, String shortCode, String vehicleRegistration, LocalDateTime clientClaimedAt) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        shortCodeRateLimiter.assertNotBlocked(guardUserId);

        Optional<Invitation> found = invitationRepository
                .findTopByShortCodeAndResident_Unit_Property_IdOrderByCreatedAtDesc(shortCode, guard.getProperty().getId());

        CheckInRejectionReason rejection = found.isEmpty() ? CheckInRejectionReason.NOT_FOUND : classifyRejection(found.get());
        if (rejection != null) {
            shortCodeRateLimiter.recordFailure(guardUserId);
            recordFailedLookup(guard, rejection);
            throw new CheckInRejectedException(rejection, rejectionMessage(rejection, found.orElse(null)));
        }

        shortCodeRateLimiter.recordSuccess(guardUserId);
        return performCheckIn(guard, found.get(), guardUserId, vehicleRegistration, clientClaimedAt);
    }

    /** null means the invitation is PENDING and within its validity window — clear to check in. */
    private CheckInRejectionReason classifyRejection(Invitation invitation) {
        LocalDateTime now = LocalDateTime.now();
        if (invitation.getStatus() == InvitationStatus.USED || invitation.getStatus() == InvitationStatus.CANCELLED) {
            return CheckInRejectionReason.ALREADY_USED;
        }
        if (now.isBefore(invitation.getValidFrom())) {
            return CheckInRejectionReason.NOT_YET_VALID;
        }
        if (now.isAfter(invitation.getValidUntil()) || invitation.getStatus() == InvitationStatus.EXPIRED) {
            return CheckInRejectionReason.EXPIRED;
        }
        return null;
    }

    private String rejectionMessage(CheckInRejectionReason reason, Invitation invitation) {
        return switch (reason) {
            case NOT_FOUND -> "No invitation with that code at this property";
            case EXPIRED -> "This invitation has expired"
                    + (invitation != null ? " (was valid until " + invitation.getValidUntil() + ")" : "");
            case NOT_YET_VALID -> "This invitation is not valid yet"
                    + (invitation != null ? " (valid from " + invitation.getValidFrom() + ")" : "");
            case ALREADY_USED -> invitation != null && invitation.getStatus() == InvitationStatus.CANCELLED
                    ? "This invitation was cancelled"
                    : "This invitation has already been used";
        };
    }

    private void recordFailedLookup(Guard guard, CheckInRejectionReason reason) {
        ShortCodeLookupAttempt attempt = new ShortCodeLookupAttempt();
        attempt.setGuard(guard);
        attempt.setProperty(guard.getProperty());
        attempt.setReason(reason);
        shortCodeLookupAttemptRepository.save(attempt);
    }

    /**
     * The write path shared by both check-in routes: mark the invitation
     * USED, create the visitor_entry, and audit both (CLAUDE.md rule 2).
     * The caller has already established the invitation is PENDING, within
     * its validity window, and at the guard's property.
     */
    private CheckInResult performCheckIn(Guard guard, Invitation invitation, Long guardUserId, String vehicleRegistration, LocalDateTime clientClaimedAt) {
        InvitationStatus previousStatus = invitation.getStatus();
        invitation.setStatus(InvitationStatus.USED);
        invitationRepository.save(invitation);
        auditLogService.record("invitation", invitation.getId(), AuditAction.UPDATE,
                guardUserId, Map.of("status", previousStatus), Map.of("status", invitation.getStatus()));

        Resident visitingResident = invitation.getResident();
        PropertyUnit unit = visitingResident.getUnit();
        String visitingResidentName = visitingResident.getUser().getFullName();

        Vehicle vehicle = null;
        if (vehicleRegistration != null && !vehicleRegistration.isBlank()) {
            vehicle = vehicleService.findOrCreate(vehicleRegistration, null, null, null, guardUserId);
        }

        VisitorEntry entry = new VisitorEntry();
        entry.setProperty(unit.getProperty());
        entry.setUnit(unit);
        entry.setInvitation(invitation);
        entry.setVehicle(vehicle);
        entry.setVisitorName(invitation.getVisitorName());
        entry.setVisitorPhone(invitation.getVisitorPhone());
        entry.setCategory(VisitorCategory.VISITOR);
        entry.setProcessedByGuard(guard);
        entry.setEnteredAt(LocalDateTime.now());
        entry.setClientClaimedAt(clientClaimedAt);
        entry.setApprovalStatus(ApprovalStatus.AUTO_APPROVED);

        VisitorEntry saved = visitorEntryRepository.save(entry);
        auditLogService.record("visitor_entry", saved.getId(), AuditAction.CREATE,
                guardUserId, null, snapshot(saved));

        boolean recognized = vehicle != null && vehicleService.isRecognized(vehicle.getId());
        return new CheckInResult(saved, recognized, visitingResidentName, recognizedOwnerNameFor(vehicle));
    }

    /** Shared by CheckInResult/WalkInResult/CheckOutResult/myHistory — null unless the vehicle is actually recognized. */
    private String recognizedOwnerNameFor(Vehicle vehicle) {
        if (vehicle == null || !vehicleService.isRecognized(vehicle.getId())) {
            return null;
        }
        return String.join(", ", vehicleService.recognizedOwnerNames(vehicle.getId()));
    }

    /**
     * Same idea as CheckInResult: visitingResidentNames/
     * recognizedVehicleOwnerName are plain Strings extracted while
     * checkInWalkIn()'s transaction is still open, not navigable entities.
     * visitingResidentNames is comma-joined and can be null (no unit picked,
     * or a unit with no residents on file yet) — unlike a QR check-in's
     * single inviting resident, a walk-in's unit can have more than one.
     */
    public record WalkInResult(
            VisitorEntry entry,
            boolean vehicleRecognized,
            String visitingResidentNames,
            String recognizedVehicleOwnerName) {
    }

    /**
     * Walk-in / unexpected visitor: no invitation code, so invitation stays
     * null (the schema's own documented meaning of that column — see
     * property_security_schema.sql). PENDING, not AUTO_APPROVED — unlike a
     * scanned check-in (backed by a resident-issued invitation), nobody has
     * actually vetted this visitor, so it's flagged for review rather than
     * silently treated as approved. There's no push/SMS channel yet to
     * drive a live approve/deny workflow off that PENDING status — it's
     * surfaced passively via occupancy/history (no invitationId) for now.
     * Audited the same as every other visitor_entry write (CLAUDE.md rule 2).
     */
    public WalkInResult checkInWalkIn(Long guardUserId, WalkInVisitorRequest request) {
        // clientClaimedAt comes in via request.clientClaimedAt()
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        PropertyUnit unit = null;
        if (request.unitId() != null) {
            unit = propertyUnitRepository.findByIdAndDeletedAtIsNull(request.unitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit " + request.unitId() + " not found"));
            if (!unit.getProperty().getId().equals(guard.getProperty().getId())) {
                throw new BadRequestException("Unit " + request.unitId() + " is not at your property");
            }
        }

        Vehicle vehicle = null;
        if (request.vehicleRegistration() != null && !request.vehicleRegistration().isBlank()) {
            vehicle = vehicleService.findOrCreate(request.vehicleRegistration(), null, null, null, guardUserId);
        }

        VisitorEntry entry = new VisitorEntry();
        entry.setProperty(guard.getProperty());
        entry.setUnit(unit);
        entry.setVehicle(vehicle);
        entry.setVisitorName(request.visitorName());
        entry.setVisitorPhone(request.visitorPhone());
        entry.setCategory(request.category() != null ? request.category() : VisitorCategory.VISITOR);
        entry.setProcessedByGuard(guard);
        entry.setEnteredAt(LocalDateTime.now());
        entry.setClientClaimedAt(request.clientClaimedAt());
        entry.setApprovalStatus(ApprovalStatus.PENDING);
        entry.setNotes(request.purpose());

        VisitorEntry saved = visitorEntryRepository.save(entry);
        auditLogService.record("visitor_entry", saved.getId(), AuditAction.CREATE,
                guardUserId, null, snapshot(saved));

        boolean recognized = vehicle != null && vehicleService.isRecognized(vehicle.getId());
        return new WalkInResult(saved, recognized, visitingResidentNamesFor(saved), recognizedOwnerNameFor(vehicle));
    }

    /**
     * Who a visitor_entry is "for", regardless of how they got checked in:
     * a QR/code entry has exactly one inviting resident; a walk-in with a
     * picked unit can have several (family members etc.) or none yet (a
     * unit added but nobody's registered as living there). Shared by
     * checkInWalkIn and checkOut so a checkout confirmation can name the
     * same resident(s) a walk-in's own check-in confirmation did.
     */
    private String visitingResidentNamesFor(VisitorEntry entry) {
        if (entry.getInvitation() != null) {
            return entry.getInvitation().getResident().getUser().getFullName();
        }
        if (entry.getUnit() == null) {
            return null;
        }
        String names = String.join(", ", residentRepository.findAllByUnit_IdAndDeletedAtIsNull(entry.getUnit().getId()).stream()
                .map(r -> r.getUser().getFullName())
                .toList());
        return names.isEmpty() ? null : names;
    }

    /**
     * Every visitor_entry for a registration. Scoped to the caller's own
     * property for a guard; scoped to every property they manage for a
     * property manager (can be more than one — see PropertyManager); ADMIN
     * (neither a guard nor a manager) is unscoped. SUPERVISOR still has no
     * property association in the schema to scope by, so it stays excluded
     * at the controller's @PreAuthorize level.
     */
    @Transactional(readOnly = true)
    public List<VisitorEntry> historyForRegistration(Long callerUserId, String registration) {
        String normalized = registration.trim().toUpperCase();

        Optional<Guard> guard = guardRepository.findByUser_IdAndDeletedAtIsNull(callerUserId);
        if (guard.isPresent()) {
            return visitorEntryRepository.findAllByVehicle_RegistrationAndProperty_IdOrderByEnteredAtDesc(
                    normalized, guard.get().getProperty().getId());
        }

        List<Long> managedPropertyIds = propertyManagerRepository.findAllByUser_IdAndDeletedAtIsNull(callerUserId).stream()
                .map(pm -> pm.getProperty().getId())
                .toList();
        if (!managedPropertyIds.isEmpty()) {
            return visitorEntryRepository.findAllByVehicle_RegistrationAndProperty_IdInOrderByEnteredAtDesc(normalized, managedPropertyIds);
        }

        return visitorEntryRepository.findAllByVehicle_RegistrationOrderByEnteredAtDesc(normalized);
    }

    /** A resident's own visitor history — entries from invitations they personally created. */
    @Transactional(readOnly = true)
    public List<VisitorEntry> myHistory(Long residentUserId) {
        Resident resident = residentRepository.findByUser_IdAndDeletedAtIsNull(residentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No resident profile found for this account"));
        return visitorEntryRepository.findAllByInvitation_Resident_IdOrderByEnteredAtDesc(resident.getId());
    }

    public boolean isVehicleRecognized(VisitorEntry entry) {
        return entry.getVehicle() != null && vehicleService.isRecognized(entry.getVehicle().getId());
    }

    /** Public wrapper for controllers that don't otherwise have a reason to depend on VehicleService directly — see MyVisitorEntriesController. */
    public String recognizedVehicleOwnerName(VisitorEntry entry) {
        return recognizedOwnerNameFor(entry.getVehicle());
    }

    /**
     * Everyone currently on site at a property (exited_at IS NULL), grouped
     * by category — all four categories always present, possibly empty.
     * Scoped the same way as vehicle history (see historyForRegistration).
     */
    @Transactional(readOnly = true)
    public Map<VisitorCategory, List<VisitorEntry>> occupancy(Long callerUserId, Long propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property " + propertyId + " not found");
        }

        assertCanAccessProperty(callerUserId, propertyId);

        Map<VisitorCategory, List<VisitorEntry>> byCategory = new EnumMap<>(VisitorCategory.class);
        for (VisitorCategory category : VisitorCategory.values()) {
            byCategory.put(category, new ArrayList<>());
        }
        for (VisitorEntry entry : visitorEntryRepository.findAllOnSiteByProperty_Id(propertyId)) {
            byCategory.get(entry.getCategory()).add(entry);
        }
        return byCategory;
    }

    /**
     * Incident-investigation lookup: "who visited on [day/range]" — the
     * literal digital equivalent of flipping a paper register to a date.
     * Deliberately not CLIENT-facing (an open-ended per-visitor browsing
     * tool for a property owner is a real privacy concern distinct from a
     * scoped incident lookup — see PRODUCT.md/this conversation's
     * decision). GUARD was excluded the same way until 2026-08-30 — a
     * guard couldn't see who'd already checked out earlier in their own
     * shift once they dropped off the occupancy list, a real operational
     * gap (dev-confirmed) distinct from PROPERTY_MANAGER/SUPERVISOR's
     * open-ended range. Opened for GUARD, but strictly narrower: today
     * only (enforced here, not just left to the frontend), own property
     * only. assertCanAccessPropertyForHistory: a guard to their own
     * property and today only, a property manager to properties they
     * manage, a supervisor to properties they supervise, ADMIN unrestricted.
     */
    @Transactional(readOnly = true)
    public List<VisitorEntry> historyForDateRange(Long callerUserId, Long propertyId, LocalDate from, LocalDate to) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property " + propertyId + " not found");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("The end date can't be before the start date");
        }
        assertCanAccessPropertyForHistory(callerUserId, propertyId, from, to);

        LocalDateTime fromInclusive = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        return visitorEntryRepository.findAllByProperty_IdAndEnteredAtBetween(propertyId, fromInclusive, toExclusive);
    }

    private void assertCanAccessPropertyForHistory(Long callerUserId, Long propertyId, LocalDate from, LocalDate to) {
        Optional<Guard> guard = guardRepository.findByUser_IdAndDeletedAtIsNull(callerUserId);
        if (guard.isPresent()) {
            if (!guard.get().getProperty().getId().equals(propertyId)) {
                throw new AccessDeniedException("This property is not yours");
            }
            LocalDate today = LocalDate.now();
            if (!from.isEqual(today) || !to.isEqual(today)) {
                throw new AccessDeniedException("Guards can only view today's history");
            }
            return;
        }

        boolean isAnyManager = propertyManagerRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        boolean isAnySupervisor = propertySupervisorRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (!isAnyManager && !isAnySupervisor) {
            return; // ADMIN — unrestricted
        }
        boolean managesThis = isAnyManager
                && propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId);
        boolean supervisesThis = isAnySupervisor
                && propertySupervisorRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId);
        if (!managesThis && !supervisesThis) {
            throw new AccessDeniedException("This property is not yours");
        }
    }

    /** Same visitingResidentNames/recognizedVehicleOwnerName reasoning as CheckInResult/WalkInResult. */
    public record CheckOutResult(
            VisitorEntry entry,
            boolean vehicleRecognized,
            String visitingResidentNames,
            String recognizedVehicleOwnerName) {
    }

    /**
     * Checks a visitor out: server-stamps exited_at and records which guard
     * processed the exit. Same property-match rule as check-in — a guard
     * can only exit visitors at their own property. Writes an audit_log
     * UPDATE row (CLAUDE.md rule 2).
     */
    public CheckOutResult checkOut(Long guardUserId, Long entryId) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        VisitorEntry entry = visitorEntryRepository.findByIdFetchVehicle(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor entry " + entryId + " not found"));

        if (entry.getExitedAt() != null) {
            throw new BadRequestException("This visitor has already exited");
        }
        if (!entry.getProperty().getId().equals(guard.getProperty().getId())) {
            throw new AccessDeniedException("This visitor entry is for a different property");
        }

        entry.setExitedAt(LocalDateTime.now());
        entry.setExitProcessedByGuard(guard);
        VisitorEntry saved = visitorEntryRepository.save(entry);

        auditLogService.record("visitor_entry", saved.getId(), AuditAction.UPDATE, guardUserId,
                Map.of("exitedAt", "null"),
                Map.of("exitedAt", saved.getExitedAt(), "exitProcessedByGuardId", guard.getId()));

        boolean recognized = isVehicleRecognized(saved);
        return new CheckOutResult(saved, recognized, visitingResidentNamesFor(saved), recognizedOwnerNameFor(saved.getVehicle()));
    }

    private void validateStatus(Invitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            String reason = switch (invitation.getStatus()) {
                case USED -> "has already been used";
                case CANCELLED -> "was cancelled";
                case EXPIRED -> "has expired";
                case PENDING -> throw new IllegalStateException("unreachable");
            };
            throw new BadRequestException("This invitation " + reason);
        }
    }

    private void validateTimeWindow(Invitation invitation) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(invitation.getValidFrom())) {
            throw new BadRequestException("This invitation is not valid yet (valid from " + invitation.getValidFrom() + ")");
        }
        if (now.isAfter(invitation.getValidUntil())) {
            throw new BadRequestException("This invitation has expired (was valid until " + invitation.getValidUntil() + ")");
        }
    }

    /**
     * Guard -> must be their one property. Property manager -> must be one
     * of the properties they manage. Neither (e.g. ADMIN) -> unrestricted.
     */
    private void assertCanAccessProperty(Long callerUserId, Long propertyId) {
        Optional<Guard> guard = guardRepository.findByUser_IdAndDeletedAtIsNull(callerUserId);
        if (guard.isPresent()) {
            if (!guard.get().getProperty().getId().equals(propertyId)) {
                throw new AccessDeniedException("This property is not yours");
            }
            return;
        }

        boolean isAnyPropertyManager = propertyManagerRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnyPropertyManager && !propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
            throw new AccessDeniedException("This property is not yours");
        }
    }

    private void validateSameProperty(Guard guard, Invitation invitation) {
        Long guardPropertyId = guard.getProperty().getId();
        Long invitationPropertyId = invitation.getResident().getUnit().getProperty().getId();
        if (!guardPropertyId.equals(invitationPropertyId)) {
            throw new AccessDeniedException("This invitation is for a different property");
        }
    }

    private Map<String, Object> snapshot(VisitorEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("propertyId", entry.getProperty().getId());
        map.put("unitId", entry.getUnit() != null ? entry.getUnit().getId() : null);
        map.put("invitationId", entry.getInvitation() != null ? entry.getInvitation().getId() : null);
        map.put("vehicleId", entry.getVehicle() != null ? entry.getVehicle().getId() : null);
        map.put("visitorName", entry.getVisitorName());
        map.put("category", entry.getCategory());
        map.put("processedByGuardId", entry.getProcessedByGuard().getId());
        map.put("enteredAt", entry.getEnteredAt());
        map.put("approvalStatus", entry.getApprovalStatus());
        return map;
    }
}
