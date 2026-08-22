package com.propertysecurity.platform.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuditLogServiceTest exercises the hash-chain math against a mocked
 * repository, which can't catch precision loss across a real DB round
 * trip. This is the test that would have caught it: a real save + reload
 * against H2 broke the chain in production against MySQL because
 * performed_at (a plain DATETIME, no fractional seconds) silently dropped
 * everything past whole seconds, so the hash computed at write time (from
 * a nanosecond-precision LocalDateTime.now()) never matched what came back
 * on verify. Fixed by truncating to seconds before hashing in
 * AuditLogService, and by forcing the same DATETIME precision in the H2
 * test schema (AuditLog.performedAt's columnDefinition) so this test
 * actually exercises that failure mode instead of H2's more forgiving
 * default precision masking it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogPersistenceIntegrationTest {

    @Autowired
    private AuditLogService auditLogService;

    @Test
    void chainSurvivesARealSaveAndReload() {
        auditLogService.record("property", 1L, AuditAction.CREATE, 1L, null, "{\"name\":\"Sunset\"}");
        auditLogService.record("property", 1L, AuditAction.UPDATE, 1L, "{\"name\":\"Sunset\"}", "{\"name\":\"Sunrise\"}");

        AuditLogService.VerificationResult result = auditLogService.verifyChain();

        assertThat(result.valid()).isTrue();
        assertThat(result.firstBrokenId()).isNull();
    }
}
