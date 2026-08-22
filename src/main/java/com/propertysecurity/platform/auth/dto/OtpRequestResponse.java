package com.propertysecurity.platform.auth.dto;

public record OtpRequestResponse(
        String message,
        int expiresInMinutes,
        // Phase 1 dev stub only: populated when app.otp.expose-code-in-response=true
        // because there is no real SMS provider yet. Null otherwise.
        String devOnlyCode
) {
}
