package com.propertysecurity.platform.visitorentry.dto;

import com.propertysecurity.platform.visitorentry.VisitorCategory;
import com.propertysecurity.platform.visitorentry.VisitorEntry;
import com.propertysecurity.platform.visitorentry.VisitorEntryService;

import java.time.LocalDateTime;

/**
 * Response for the QR/manual-code check-in flow specifically (VisitorEntry
 * Controller.scan / VisitorEntryService.checkIn) — deliberately its own
 * type rather than an addition to the shared VisitorEntryResponse, which
 * is built from several other queries that don't have this data available.
 * Confirms identity, not just that a code was valid: visitingResidentName
 * is who the visitor is actually here to see, and recognizedVehicleOwnerName
 * says whose vehicle a "Recognized" match actually is, rather than an
 * anonymous plate match.
 */
public record VisitorCheckInResponse(
        Long id,
        Long propertyId,
        String visitorName,
        VisitorCategory category,
        String visitingResidentName,
        String vehicleRegistration,
        boolean vehicleRecognized,
        String recognizedVehicleOwnerName,
        LocalDateTime enteredAt,
        LocalDateTime clientClaimedAt
) {
    public static VisitorCheckInResponse from(VisitorEntryService.CheckInResult result) {
        VisitorEntry entry = result.entry();
        return new VisitorCheckInResponse(
                entry.getId(),
                entry.getProperty().getId(),
                entry.getVisitorName(),
                entry.getCategory(),
                result.visitingResidentName(),
                entry.getVehicle() != null ? entry.getVehicle().getRegistration() : null,
                result.vehicleRecognized(),
                result.recognizedVehicleOwnerName(),
                entry.getEnteredAt(),
                entry.getClientClaimedAt());
    }
}
