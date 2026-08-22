package com.propertysecurity.platform.unit.dto;

import com.propertysecurity.platform.unit.PropertyUnit;

import java.time.LocalDateTime;

public record UnitResponse(
        Long id,
        Long propertyId,
        String unitNumber,
        LocalDateTime createdAt
) {
    public static UnitResponse from(PropertyUnit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getProperty().getId(),
                unit.getUnitNumber(),
                unit.getCreatedAt());
    }
}
