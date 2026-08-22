package com.propertysecurity.platform.resident.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResidentRequest(
        @NotNull Long unitId,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phoneNumber,
        @Email @Size(max = 150) String email
) {
}
