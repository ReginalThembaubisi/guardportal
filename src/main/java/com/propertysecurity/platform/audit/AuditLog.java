package com.propertysecurity.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Not a BaseEntity: no deleted_at, no update path at all. Rows are
 * write-once — the app's DB user is INSERT-only on this table at the MySQL
 * grant level (see docs/db_grants_audit_log.sql), and record_hash chains
 * each row to the previous one so any tampering breaks the chain.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_name", nullable = false, length = 50)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "performed_by_user_id")
    private Long performedByUserId;

    @Column(name = "before_value", columnDefinition = "json")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "json")
    private String afterValue;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(name = "record_hash", nullable = false, length = 64)
    private String recordHash;
}
