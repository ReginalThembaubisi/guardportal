package com.propertysecurity.platform.visitorentry.dto;

import com.propertysecurity.platform.visitorentry.VisitorCategory;
import com.propertysecurity.platform.visitorentry.VisitorEntry;

import java.time.LocalDateTime;

/**
 * A resident's own "My Visitor History" — same recognizedVehicleOwnerName
 * idea as the guard-facing check-in/walk-in/check-out responses. There's no
 * visitingResidentName field here: every row already belongs to this
 * resident's own invitations, so it would just repeat their own name.
 */
public record VisitorHistoryForResidentResponse(
        Long id,
        String visitorName,
        VisitorCategory category,
        String vehicleRegistration,
        boolean vehicleRecognized,
        String recognizedVehicleOwnerName,
        LocalDateTime enteredAt,
        LocalDateTime exitedAt
) {
    public static VisitorHistoryForResidentResponse from(VisitorEntry entry, boolean vehicleRecognized, String recognizedVehicleOwnerName) {
        return new VisitorHistoryForResidentResponse(
                entry.getId(),
                entry.getVisitorName(),
                entry.getCategory(),
                entry.getVehicle() != null ? entry.getVehicle().getRegistration() : null,
                vehicleRecognized,
                recognizedVehicleOwnerName,
                entry.getEnteredAt(),
                entry.getExitedAt());
    }
}
