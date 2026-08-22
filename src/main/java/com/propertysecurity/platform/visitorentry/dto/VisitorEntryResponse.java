package com.propertysecurity.platform.visitorentry.dto;

import com.propertysecurity.platform.visitorentry.ApprovalStatus;
import com.propertysecurity.platform.visitorentry.VisitorCategory;
import com.propertysecurity.platform.visitorentry.VisitorEntry;

import java.time.LocalDateTime;

public record VisitorEntryResponse(
        Long id,
        Long propertyId,
        Long unitId,
        Long invitationId,
        String visitorName,
        String visitorPhone,
        VisitorCategory category,
        ApprovalStatus approvalStatus,
        Long processedByGuardId,
        LocalDateTime enteredAt,
        LocalDateTime exitedAt,
        String notes,
        LocalDateTime createdAt
) {
    public static VisitorEntryResponse from(VisitorEntry entry) {
        return new VisitorEntryResponse(
                entry.getId(),
                entry.getProperty().getId(),
                entry.getUnit() != null ? entry.getUnit().getId() : null,
                entry.getInvitation() != null ? entry.getInvitation().getId() : null,
                entry.getVisitorName(),
                entry.getVisitorPhone(),
                entry.getCategory(),
                entry.getApprovalStatus(),
                entry.getProcessedByGuard().getId(),
                entry.getEnteredAt(),
                entry.getExitedAt(),
                entry.getNotes(),
                entry.getCreatedAt());
    }
}
