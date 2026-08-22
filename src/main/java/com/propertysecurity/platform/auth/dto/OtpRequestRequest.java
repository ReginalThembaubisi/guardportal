package com.propertysecurity.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpRequestRequest(
        @NotBlank String phoneNumber
) {
}
