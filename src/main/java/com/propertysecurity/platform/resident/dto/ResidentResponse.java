package com.propertysecurity.platform.resident.dto;

import com.propertysecurity.platform.resident.Resident;

import java.time.LocalDateTime;

public record ResidentResponse(
        Long id,
        Long userId,
        String fullName,
        String phoneNumber,
        String email,
        Long unitId,
        String unitNumber,
        LocalDateTime createdAt
) {
    public static ResidentResponse from(Resident resident) {
        return new ResidentResponse(
                resident.getId(),
                resident.getUser().getId(),
                resident.getUser().getFullName(),
                resident.getUser().getPhoneNumber(),
                resident.getUser().getEmail(),
                resident.getUnit().getId(),
                resident.getUnit().getUnitNumber(),
                resident.getCreatedAt());
    }
}
