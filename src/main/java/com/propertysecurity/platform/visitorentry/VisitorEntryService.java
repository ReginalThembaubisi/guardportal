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
import com.propertysecurity.platform.unit.PropertyUnit;
import com.propertysecurity.platform.vehicle.Vehicle;
import com.propertysecurity.platform.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class VisitorEntryService {

    private final VisitorEntryRepository visitorEntryRepository;
    private final InvitationRepository invitationRepository;
    private final GuardRepository guardRepository;
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
     * Every visitor_entry for a registration. Scoped to the caller's own
     * property when they're a guard; unscoped (all properties) otherwise —
     * currently that's only ADMIN, since SUPERVISOR/PROPERTY_MANAGER have no
     * property association in the schema to scope by yet.
     */
    @Transactional(readOnly = true)
    public List<VisitorEntry> historyForRegistration(Long callerUserId, String registration) {
        String normalized = registration.trim().toUpperCase();
        return guardRepository.findByUser_IdAndDeletedAtIsNull(callerUserId)
                .map(guard -> visitorEntryRepository.findAllByVehicle_RegistrationAndProperty_IdOrderByEnteredAtDesc(
                        normalized, guard.getProperty().getId()))
                .orElseGet(() -> visitorEntryRepository.findAllByVehicle_RegistrationOrderByEnteredAtDesc(normalized));
    }

    public boolean isVehicleRecognized(VisitorEntry entry) {
        return entry.getVehicle() != null && vehicleService.isRecognized(entry.getVehicle().getId());
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
