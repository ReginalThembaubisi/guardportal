package com.propertysecurity.platform.audit;

import com.propertysecurity.platform.audit.dto.AuditVerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/verify")
    public AuditVerificationResponse verify() {
        return AuditVerificationResponse.from(auditLogService.verifyChain());
    }
}
