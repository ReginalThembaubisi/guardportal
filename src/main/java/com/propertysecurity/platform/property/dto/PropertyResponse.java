package com.propertysecurity.platform.property.dto;

import com.propertysecurity.platform.property.Property;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PropertyResponse(
        Long id,
        String name,
        String address,
        String timezone,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer geoToleranceMeters,
        LocalDateTime createdAt
) {
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getAddress(),
                property.getTimezone(),
                property.getLatitude(),
                property.getLongitude(),
                property.getGeoToleranceMeters(),
                property.getCreatedAt());
    }
}
