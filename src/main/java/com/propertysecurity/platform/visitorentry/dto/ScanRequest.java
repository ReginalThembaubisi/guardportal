package com.propertysecurity.platform.visitorentry.dto;

import jakarta.validation.constraints.NotBlank;

public record ScanRequest(
        @NotBlank String qrToken
) {
}
