package com.propertysecurity.platform.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Nothing in Phase 1 writes to audit_log yet (see AuditLogService javadoc),
 * so this is the only thing that actually exercises the hash-chain math
 * before it's wired into a real write path in Phase 2.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;
    private List<AuditLog> savedRows;

    @BeforeEach
    void setUp() {
        savedRows = new ArrayList<>();
        auditLogService = new AuditLogService(auditLogRepository, JsonMapper.builder().build());

        lenient().when(auditLogRepository.save(any())).thenAnswer(inv -> {
            AuditLog row = inv.getArgument(0);
            row.setId((long) (savedRows.size() + 1));
            savedRows.add(row);
            return row;
        });
        lenient().when(auditLogRepository.findTopByOrderByIdDesc()).thenAnswer(inv ->
                savedRows.isEmpty() ? Optional.empty() : Optional.of(savedRows.get(savedRows.size() - 1)));
        lenient().when(auditLogRepository.findAllByOrderByIdAsc()).thenAnswer(inv -> new ArrayList<>(savedRows));
    }

    @Test
    void firstRowChainsFromEmptyString() {
        AuditLog row = auditLogService.record("property", 1L, AuditAction.CREATE, 1L, null, "{\"name\":\"Sunset\"}");
        assertThat(row.getRecordHash()).isNotBlank().hasSize(64);
    }

    @Test
    void secondRowChainsFromFirstRowsHash() {
        AuditLog first = auditLogService.record("property", 1L, AuditAction.CREATE, 1L, null, "{\"name\":\"Sunset\"}");
        AuditLog second = auditLogService.record("property", 1L, AuditAction.UPDATE, 1L, "{\"name\":\"Sunset\"}", "{\"name\":\"Sunrise\"}");

        assertThat(second.getRecordHash()).isNotEqualTo(first.getRecordHash());

        AuditLogService.VerificationResult result = auditLogService.verifyChain();
        assertThat(result.valid()).isTrue();
        assertThat(result.firstBrokenId()).isNull();
    }

    @Test
    void verifyChainDetectsTamperedRow() {
        auditLogService.record("property", 1L, AuditAction.CREATE, 1L, null, "{\"name\":\"Sunset\"}");
        AuditLog second = auditLogService.record("property", 1L, AuditAction.UPDATE, 1L, "{\"name\":\"Sunset\"}", "{\"name\":\"Sunrise\"}");
        auditLogService.record("property", 1L, AuditAction.UPDATE, 1L, "{\"name\":\"Sunrise\"}", "{\"name\":\"Moonlight\"}");

        // Simulate a row edited after the fact directly in the DB, bypassing
        // the app entirely — this is exactly the scenario the hash chain
        // exists to catch.
        second.setAfterValue("{\"name\":\"TAMPERED\"}");

        AuditLogService.VerificationResult result = auditLogService.verifyChain();
        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenId()).isEqualTo(second.getId());
    }

    @Test
    void emptyChainIsValid() {
        AuditLogService.VerificationResult result = auditLogService.verifyChain();
        assertThat(result.valid()).isTrue();
        assertThat(result.firstBrokenId()).isNull();
    }
}
