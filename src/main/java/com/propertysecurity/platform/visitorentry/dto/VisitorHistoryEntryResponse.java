package com.propertysecurity.platform.visitorentry.dto;

import com.propertysecurity.platform.visitorentry.ApprovalStatus;
import com.propertysecurity.platform.visitorentry.VisitorCategory;
import com.propertysecurity.platform.visitorentry.VisitorEntry;

import java.time.LocalDateTime;

/**
 * Deliberately its own type rather than reusing VisitorEntryResponse: this
 * one needs unitNumber (so an incident-investigation table doesn't just
 * show a bare id), and unlike VisitorEntryResponse's other callers, the
 * query backing this feature (VisitorEntryRepository.
 * findAllByProperty_IdAndEnteredAtBetween) is guaranteed to fetch-join
 * unit. Reusing the shared DTO would have meant adding unitNumber
 * everywhere VisitorEntryResponse is built, several of which don't
 * fetch-join unit and would start throwing LazyInitializationException
 * outside their transaction.
 */
public record VisitorHistoryEntryResponse(
        Long id,
        String visitorName,
        String visitorPhone,
        Long unitId,
        String unitNumber,
        VisitorCategory category,
        ApprovalStatus approvalStatus,
        String vehicleRegistration,
        LocalDateTime enteredAt,
        LocalDateTime exitedAt,
        String notes
) {
    public static VisitorHistoryEntryResponse from(VisitorEntry entry) {
        return new VisitorHistoryEntryResponse(
                entry.getId(),
                entry.getVisitorName(),
                entry.getVisitorPhone(),
                entry.getUnit() != null ? entry.getUnit().getId() : null,
                entry.getUnit() != null ? entry.getUnit().getUnitNumber() : null,
                entry.getCategory(),
                entry.getApprovalStatus(),
                entry.getVehicle() != null ? entry.getVehicle().getRegistration() : null,
                entry.getEnteredAt(),
                entry.getExitedAt(),
                entry.getNotes());
    }
}
