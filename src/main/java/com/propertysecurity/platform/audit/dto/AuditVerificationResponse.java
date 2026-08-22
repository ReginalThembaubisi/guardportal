package com.propertysecurity.platform.audit.dto;

import com.propertysecurity.platform.audit.AuditLogService;

public record AuditVerificationResponse(boolean valid, Long firstBrokenId) {
    public static AuditVerificationResponse from(AuditLogService.VerificationResult result) {
        return new AuditVerificationResponse(result.valid(), result.firstBrokenId());
    }
}
