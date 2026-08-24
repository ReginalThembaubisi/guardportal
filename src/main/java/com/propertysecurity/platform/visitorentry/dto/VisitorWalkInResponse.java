package com.propertysecurity.platform.visitorentry.dto;

import com.propertysecurity.platform.visitorentry.VisitorCategory;
import com.propertysecurity.platform.visitorentry.VisitorEntry;
import com.propertysecurity.platform.visitorentry.VisitorEntryService;

import java.time.LocalDateTime;

/**
 * Walk-in counterpart to VisitorCheckInResponse — same reasoning for being
 * its own type (see that class's comment): built from
 * VisitorEntryService.WalkInResult, whose visitingResidentNames/
 * recognizedVehicleOwnerName are already-extracted Strings, safe to read
 * regardless of transaction state.
 */
public record VisitorWalkInResponse(
        Long id,
        Long propertyId,
        String visitorName,
        VisitorCategory category,
        Long unitId,
        String visitingResidentNames,
        String vehicleRegistration,
        boolean vehicleRecognized,
        String recognizedVehicleOwnerName,
        LocalDateTime enteredAt,
        String notes
) {
    public static VisitorWalkInResponse from(VisitorEntryService.WalkInResult result) {
        VisitorEntry entry = result.entry();
        return new VisitorWalkInResponse(
                entry.getId(),
                entry.getProperty().getId(),
                entry.getVisitorName(),
                entry.getCategory(),
                entry.getUnit() != null ? entry.getUnit().getId() : null,
                result.visitingResidentNames(),
                entry.getVehicle() != null ? entry.getVehicle().getRegistration() : null,
                result.vehicleRecognized(),
                result.recognizedVehicleOwnerName(),
                entry.getEnteredAt(),
                entry.getNotes());
    }
}
