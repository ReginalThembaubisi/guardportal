package com.propertysecurity.platform.visitorentry.dto;

import com.propertysecurity.platform.visitorentry.VisitorEntry;
import com.propertysecurity.platform.visitorentry.VisitorEntryService;

import java.time.LocalDateTime;

/**
 * Check-out counterpart to VisitorCheckInResponse/VisitorWalkInResponse —
 * same reasoning for being its own type. Built from
 * VisitorEntryService.CheckOutResult, whose visitingResidentNames/
 * recognizedVehicleOwnerName are already-extracted Strings.
 */
public record VisitorCheckOutResponse(
        Long id,
        String visitorName,
        String visitingResidentNames,
        String vehicleRegistration,
        boolean vehicleRecognized,
        String recognizedVehicleOwnerName,
        LocalDateTime enteredAt,
        LocalDateTime exitedAt
) {
    public static VisitorCheckOutResponse from(VisitorEntryService.CheckOutResult result) {
        VisitorEntry entry = result.entry();
        return new VisitorCheckOutResponse(
                entry.getId(),
                entry.getVisitorName(),
                result.visitingResidentNames(),
                entry.getVehicle() != null ? entry.getVehicle().getRegistration() : null,
                result.vehicleRecognized(),
                result.recognizedVehicleOwnerName(),
                entry.getEnteredAt(),
                entry.getExitedAt());
    }
}
