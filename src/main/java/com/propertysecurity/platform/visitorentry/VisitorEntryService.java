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

    public record CheckInResult(VisitorEntry entry, boolean vehicleRecognized) {
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
    public CheckInResult checkIn(Long guardUserId, String qrToken, String vehicleRegistration) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        Invitation invitation = invitationRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid check-in code"));

        validateStatus(invitation);
        validateTimeWindow(invitation);
        validateSameProperty(guard, invitation);

        InvitationStatus previousStatus = invitation.getStatus();
        invitation.setStatus(InvitationStatus.USED);
        invitationRepository.save(invitation);
        auditLogService.record("invitation", invitation.getId(), AuditAction.UPDATE,
                guardUserId, Map.of("status", previousStatus), Map.of("status", invitation.getStatus()));

        PropertyUnit unit = invitation.getResident().getUnit();

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
        entry.setApprovalStatus(ApprovalStatus.AUTO_APPROVED);

        VisitorEntry saved = visitorEntryRepository.save(entry);
        auditLogService.record("visitor_entry", saved.getId(), AuditAction.CREATE,
                guardUserId, null, snapshot(saved));

        boolean recognized = vehicle != null && vehicleService.isRecognized(vehicle.getId());
        return new CheckInResult(saved, recognized);
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
    public VisitorEntry checkInWalkIn(Long guardUserId, WalkInVisitorRequest request) {
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
        entry.setApprovalStatus(ApprovalStatus.PENDING);
        entry.setNotes(request.purpose());

        VisitorEntry saved = visitorEntryRepository.save(entry);
        auditLogService.record("visitor_entry", saved.getId(), AuditAction.CREATE,
                guardUserId, null, snapshot(saved));
        return saved;
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
     * Deliberately not GUARD-facing (guards get current occupancy, not a
     * history browser) and deliberately not CLIENT-facing (an open-ended
     * per-visitor browsing tool for a property owner is a real privacy
     * concern distinct from a scoped incident lookup — see PRODUCT.md/this
     * conversation's decision). Scoped like IncidentService.
     * assertCanAccessProperty: a property manager to properties they
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
        assertCanAccessPropertyForHistory(callerUserId, propertyId);

        LocalDateTime fromInclusive = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        return visitorEntryRepository.findAllByProperty_IdAndEnteredAtBetween(propertyId, fromInclusive, toExclusive);
    }

    private void assertCanAccessPropertyForHistory(Long callerUserId, Long propertyId) {
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

    /**
     * Checks a visitor out: server-stamps exited_at and records which guard
     * processed the exit. Same property-match rule as check-in — a guard
     * can only exit visitors at their own property. Writes an audit_log
     * UPDATE row (CLAUDE.md rule 2).
     */
    public VisitorEntry checkOut(Long guardUserId, Long entryId) {
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

        return saved;
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
