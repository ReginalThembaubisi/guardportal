package com.propertysecurity.platform.resident.dto;

import com.propertysecurity.platform.resident.Resident;

/** A resident's own unit/property — just enough for the Home page header. */
public record ResidentProfileResponse(
        String fullName,
        String unitNumber,
        String propertyName
) {
    public static ResidentProfileResponse from(Resident resident) {
        return new ResidentProfileResponse(
                resident.getUser().getFullName(),
                resident.getUnit().getUnitNumber(),
                resident.getUnit().getProperty().getName());
    }
}
