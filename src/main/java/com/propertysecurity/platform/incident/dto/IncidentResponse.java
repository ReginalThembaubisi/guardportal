package com.propertysecurity.platform.incident.dto;

import com.propertysecurity.platform.incident.Incident;
import com.propertysecurity.platform.incident.IncidentSeverity;
import com.propertysecurity.platform.incident.IncidentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record IncidentResponse(
        Long id,
        Long propertyId,
        Long reportedByGuardId,
        String reportedByGuardName,
        Long shiftId,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime reportedAt,
        LocalDateTime clientClaimedAt,
        List<IncidentMediaResponse> media,
        LocalDateTime createdAt
) {
    public static IncidentResponse from(Incident incident, List<IncidentMediaResponse> media) {
        return new IncidentResponse(
                incident.getId(),
                incident.getProperty().getId(),
                incident.getReportedByGuard().getId(),
                incident.getReportedByGuard().getUser().getFullName(),
                incident.getShift().getId(),
                incident.getDescription(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getLatitude(),
                incident.getLongitude(),
                incident.getReportedAt(),
                incident.getClientClaimedAt(),
                media,
                incident.getCreatedAt());
    }
}
