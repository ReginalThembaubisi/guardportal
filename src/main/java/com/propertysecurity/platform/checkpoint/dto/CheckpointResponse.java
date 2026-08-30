package com.propertysecurity.platform.checkpoint.dto;

import com.propertysecurity.platform.checkpoint.Checkpoint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CheckpointResponse(
        Long id,
        Long propertyId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer geoToleranceMeters,
        LocalDateTime createdAt
) {
    public static CheckpointResponse from(Checkpoint checkpoint) {
        return new CheckpointResponse(
                checkpoint.getId(),
                checkpoint.getProperty().getId(),
                checkpoint.getName(),
                checkpoint.getLatitude(),
                checkpoint.getLongitude(),
                checkpoint.getGeoToleranceMeters(),
                checkpoint.getCreatedAt());
    }
}
