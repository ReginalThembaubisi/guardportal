package com.propertysecurity.platform.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/**
 * Writes hash-chained audit_log rows and verifies the chain. Not yet wired
 * into any Phase 1 write path — CLAUDE.md's audit rule covers writes to
 * visitor_entry, invitation, and vehicle, none of which exist before Phase
 * 2. This exists now, ahead of that need, per docs/audit_trail_design.md's
 * "do immediately" guidance, so integrity holds from the very first real
 * write instead of being retrofitted onto data that already lacks it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public record VerificationResult(boolean valid, Long firstBrokenId) {
    }

    public AuditLog record(String entityName, Long entityId, AuditAction action,
                            Long performedByUserId, Object beforeValue, Object afterValue) {
        // audit_log.performed_at is a plain DATETIME (no fractional seconds,
        // per docs/property_security_schema.sql), so MySQL silently drops
        // sub-second precision on write. Truncate here so the value hashed
        // now is byte-for-byte what verifyChain() reads back later — without
        // this, every row's hash would mismatch itself on the next verify.
        LocalDateTime performedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String beforeJson = toJson(beforeValue);
        String afterJson = toJson(afterValue);
        String previousHash = auditLogRepository.findTopByOrderByIdDesc()
                .map(AuditLog::getRecordHash)
                .orElse("");

        AuditLog log = new AuditLog();
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setPerformedByUserId(performedByUserId);
        log.setBeforeValue(beforeJson);
        log.setAfterValue(afterJson);
        log.setPerformedAt(performedAt);
        log.setRecordHash(computeHash(previousHash, entityName, entityId, action, beforeJson, afterJson, performedAt));

        return auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public VerificationResult verifyChain() {
        List<AuditLog> rows = auditLogRepository.findAllByOrderByIdAsc();
        String previousHash = "";
        for (AuditLog row : rows) {
            String expected = computeHash(previousHash, row.getEntityName(), row.getEntityId(), row.getAction(),
                    row.getBeforeValue(), row.getAfterValue(), row.getPerformedAt());
            if (!expected.equals(row.getRecordHash())) {
                return new VerificationResult(false, row.getId());
            }
            previousHash = row.getRecordHash();
        }
        return new VerificationResult(true, null);
    }

    private String computeHash(String previousHash, String entityName, Long entityId, AuditAction action,
                                String beforeJson, String afterJson, LocalDateTime performedAt) {
        String input = previousHash
                + entityName
                + entityId
                + action.name()
                + (beforeJson == null ? "" : beforeJson)
                + (afterJson == null ? "" : afterJson)
                + performedAt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
