package com.propertysecurity.platform.property.dto;

import com.propertysecurity.platform.property.Property;

import java.time.LocalDateTime;

public record PropertyResponse(
        Long id,
        String name,
        String address,
        String timezone,
        LocalDateTime createdAt
) {
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getAddress(),
                property.getTimezone(),
                property.getCreatedAt());
    }
}
