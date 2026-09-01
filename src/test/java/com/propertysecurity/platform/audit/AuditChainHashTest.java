package com.propertysecurity.platform.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Verifies that the hash recomputation algorithm documented in §4 of the
 * evidence pack precisely describes what AuditLogService.computeHash()
 * actually computes.
 *
 * Any divergence here means the PDF's central claim — "recompute any row's
 * hash using only this document" — is false. The tests go through the public
 * record() API because computeHash() is private.
 */
@ExtendWith(MockitoExtension.class)
class AuditChainHashTest {

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

    /**
     * §4 algorithm, first row: previousHash is empty string, beforeValue is null.
     *
     * This is the baseline — every other test builds on it knowing it holds.
     */
    @Test
    void section4AlgorithmMatchesServiceForFirstRow() throws NoSuchAlgorithmException {
        // null beforeValue exercises the "empty string not the literal null" part of §4
        auditLogService.record("incident", 99L, AuditAction.CREATE, 7L, null, Map.of("severity", "HIGH"));

        AuditLog row = savedRows.get(0);

        // §4 verbatim: SHA-256 of the UTF-8 bytes of the concatenation of exactly 7 fields
        // with no separator between them:
        //   1. previousHash  — "" for the first row
        //   2. entityName
        //   3. entityId      (as decimal integer string)
        //   4. action        (enum name, e.g. CREATE)
        //   5. beforeValue   — JSON string, or "" (not "null") when the column is NULL
        //   6. afterValue    — same null-handling as beforeValue
        //   7. performedAt   — as produced by LocalDateTime.toString()
        String concatInput = ""                                                             // previousHash
                + row.getEntityName()                                                       // "incident"
                + row.getEntityId()                                                         // "99"
                + row.getAction().name()                                                    // "CREATE"
                + (row.getBeforeValue() == null ? "" : row.getBeforeValue())               // "" (null → "")
                + (row.getAfterValue() == null ? "" : row.getAfterValue())                 // serialised JSON
                + row.getPerformedAt().toString();                                          // LocalDateTime.toString()

        String recomputed = sha256Hex(concatInput);

        assertThat(recomputed).isEqualTo(row.getRecordHash());
    }

    /**
     * §4 algorithm, non-first row: previousHash is the prior row's stored hash.
     */
    @Test
    void section4AlgorithmMatchesServiceForChainedRow() throws NoSuchAlgorithmException {
        auditLogService.record("incident", 99L, AuditAction.CREATE, 7L, null, Map.of("severity", "HIGH"));
        auditLogService.record("incident", 99L, AuditAction.UPDATE, 7L,
                Map.of("severity", "HIGH"), Map.of("severity", "CRITICAL"));

        AuditLog first  = savedRows.get(0);
        AuditLog second = savedRows.get(1);

        // The second row uses the first row's record_hash as its previousHash field.
        String concatInput = first.getRecordHash()
                + second.getEntityName()
                + second.getEntityId()
                + second.getAction().name()
                + (second.getBeforeValue() == null ? "" : second.getBeforeValue())
                + (second.getAfterValue() == null ? "" : second.getAfterValue())
                + second.getPerformedAt().toString();

        String recomputed = sha256Hex(concatInput);

        assertThat(recomputed).isEqualTo(second.getRecordHash());
    }

    /**
     * §4 documents this JDK edge case: when a LocalDateTime has zero seconds
     * and zero nanoseconds, LocalDateTime.toString() omits the seconds component.
     *
     * For example: LocalDateTime.of(2026, 9, 1, 14, 0, 0).toString()
     *              produces "2026-09-01T14:00", NOT "2026-09-01T14:00:00".
     *
     * A recomputed hash for a row stored at exactly HH:mm:00 must use the
     * short form. Using "HH:mm:00" instead would silently produce a different
     * 64-character hash — one that looks valid but will never match.
     */
    @Test
    void minuteBoundaryTimestampSecondsAreOmittedByJdk() throws NoSuchAlgorithmException {
        LocalDateTime atMinute      = LocalDateTime.of(2026, 9, 1, 14, 0, 0);
        LocalDateTime oneSecondLater = LocalDateTime.of(2026, 9, 1, 14, 0, 1);

        // The JDK behaviour §4 documents
        assertThat(atMinute.toString()).isEqualTo("2026-09-01T14:00");
        assertThat(oneSecondLater.toString()).isEqualTo("2026-09-01T14:00:01");

        // Because the toString representations differ, any hash that includes
        // performedAt.toString() will differ between the two timestamps —
        // even though they look the same at minute-level display precision.
        String base = "incident991CREATE";
        assertThat(sha256Hex(base + atMinute)).isNotEqualTo(sha256Hex(base + oneSecondLater));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String sha256Hex(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}
