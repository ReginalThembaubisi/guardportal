package com.propertysecurity.platform.visitorentry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScanRequest(
        @NotBlank String qrToken,
        /** Registration plate only — see docs on manual capture scope. Optional; no vehicle if omitted. */
        @Size(max = 20) String vehicleRegistration
) {
}
