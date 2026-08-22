-- =====================================================================
-- AUDIT LOG — pulled forward from Phase 5+ per docs/audit_trail_design.md:
-- "Do immediately (cheap, foundational): hash-chain the audit_log table,
-- lock DB permissions so the app can only INSERT into audit_log,
-- server-side timestamps everywhere." Table shape is
-- docs/property_security_schema.sql section 6, plus the record_hash
-- column the hash-chain design calls for.
--
-- Nothing writes to this table yet — Phase 1 doesn't touch visitor_entry,
-- invitation, or vehicle, so there's nothing to audit until Phase 2. The
-- table and chain-verification plumbing exist now so integrity holds from
-- the first real write, per the design doc's reasoning (retrofitting this
-- later means the early history was never protected).
--
-- IMPORTANT: after this migration runs, the app's DB user must be locked
-- to INSERT-only on this table at the MySQL grant level — Flyway can't do
-- that itself (the app user isn't granted WITH GRANT OPTION, by design).
-- See docs/db_grants_audit_log.sql for the one-time admin step.
-- =====================================================================

CREATE TABLE audit_log (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_name             VARCHAR(50)     NOT NULL,     -- e.g. "visitor_entry"
    entity_id               BIGINT          NOT NULL,
    action                  ENUM('CREATE','UPDATE','SOFT_DELETE') NOT NULL,
    performed_by_user_id    BIGINT          NULL,
    before_value            JSON            NULL,
    after_value             JSON            NULL,
    performed_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    record_hash             CHAR(64)        NOT NULL,     -- SHA-256 hex digest, chained to the previous row
    INDEX idx_audit_entity (entity_name, entity_id)
);
