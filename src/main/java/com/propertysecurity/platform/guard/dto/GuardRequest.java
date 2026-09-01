package com.propertysecurity.platform.guard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GuardRequest(
        @NotNull Long propertyId,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phoneNumber,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 30) String badgeNumber,
        @Size(max = 20) String psiraNumber,
        @Size(max = 5) String psiraGrade,
        LocalDate psiraExpiry
) {
}
