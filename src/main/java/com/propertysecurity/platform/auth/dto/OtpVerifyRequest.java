package com.propertysecurity.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        @NotBlank String phoneNumber,
        @NotBlank String code
) {
}
