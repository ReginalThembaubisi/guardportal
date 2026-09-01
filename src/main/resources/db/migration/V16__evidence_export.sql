-- Export attempts for incident evidence packs. Written before PDF rendering so a
-- failed render still leaves a record — an over-recorded attempt is safer than an
-- unrecorded disclosure. Append-only; no deleted_at (same rationale as audit_log).
CREATE TABLE evidence_export (
    id                   BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    incident_id          BIGINT      NOT NULL,
    exported_by_user_id  BIGINT      NOT NULL,
    exported_at          DATETIME    NOT NULL,
    chain_valid          TINYINT(1)  NOT NULL,
    chain_row_count      BIGINT      NOT NULL,
    reference            VARCHAR(36) NOT NULL UNIQUE,
    CONSTRAINT fk_ee_incident FOREIGN KEY (incident_id)         REFERENCES incident(id),
    CONSTRAINT fk_ee_user     FOREIGN KEY (exported_by_user_id) REFERENCES app_user(id)
);
