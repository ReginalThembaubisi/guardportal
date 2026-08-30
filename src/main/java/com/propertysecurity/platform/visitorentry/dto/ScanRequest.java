package com.propertysecurity.platform.visitorentry.dto;

import jakarta.validation.constraints.Size;

/**
 * Exactly one of qrToken/shortCode must be present — enforced in
 * VisitorEntryController, not here, since "exactly one of two optional
 * fields" isn't expressible with per-field bean validation.
 */
public record ScanRequest(
        @Size(max = 36) String qrToken,
        @Size(min = 6, max = 6) String shortCode,
        /** Registration plate only — see docs on manual capture scope. Optional; no vehicle if omitted. */
        @Size(max = 20) String vehicleRegistration
) {
}
