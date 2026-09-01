package com.propertysecurity.platform.guard.dto;

import com.propertysecurity.platform.guard.Guard;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GuardResponse(
        Long id,
        Long userId,
        String fullName,
        String phoneNumber,
        String email,
        Long propertyId,
        String propertyName,
        String badgeNumber,
        String psiraNumber,
        String psiraGrade,
        LocalDate psiraExpiry,
        LocalDateTime createdAt
) {
    public static GuardResponse from(Guard guard) {
        return new GuardResponse(
                guard.getId(),
                guard.getUser().getId(),
                guard.getUser().getFullName(),
                guard.getUser().getPhoneNumber(),
                guard.getUser().getEmail(),
                guard.getProperty().getId(),
                guard.getProperty().getName(),
                guard.getBadgeNumber(),
                guard.getPsiraNumber(),
                guard.getPsiraGrade(),
                guard.getPsiraExpiry(),
                guard.getCreatedAt());
    }
}
