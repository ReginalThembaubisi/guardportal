-- =====================================================================
-- Not in docs/property_security_schema.sql — a genuine structural
-- addition, approved by the dev on 2026-08-23 per CLAUDE.md's "flag and
-- explain why first" rule, and (like V6/V7) an explicit override of
-- build_plan.md's "don't start Phase 5 before pilot feedback" note.
--
-- Why: incident management (Phase 5) needs somewhere to store a guard's
-- incident report and its photo evidence. property_supervisor closes a
-- gap flagged repeatedly since Phase 1/3/4 (SUPERVISOR has never had a
-- property association in the schema, so every property-scoped endpoint
-- so far has excluded it) — this feature is the first to explicitly name
-- SUPERVISOR as a viewer, so the gap gets closed here instead of excluded
-- again.
-- =====================================================================

-- Mirrors property_manager exactly — see that table's own migration
-- comment (V5) for the reasoning. A supervisor can be assigned to more
-- than one property, same as a property manager.
CREATE TABLE property_supervisor (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    property_id     BIGINT          NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL,
    CONSTRAINT fk_psup_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_psup_property FOREIGN KEY (property_id) REFERENCES property(id),
    UNIQUE KEY uq_psup_user_property (user_id, property_id)
);

-- Append-only, like visitor_entry/shift/checkpoint_scan: an incident
-- report is never deleted, only status-transitioned (OPEN ->
-- INVESTIGATING -> RESOLVED, not necessarily linear). shift_id records
-- which open shift the guard was on when they reported it, same
-- provenance pattern as checkpoint_scan.
CREATE TABLE incident (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id             BIGINT          NOT NULL,
    reported_by_guard_id    BIGINT          NOT NULL,
    shift_id                BIGINT          NOT NULL,
    description             TEXT            NOT NULL,
    severity                ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    status                  ENUM('OPEN','INVESTIGATING','RESOLVED') NOT NULL DEFAULT 'OPEN',
    latitude                DECIMAL(10,7)   NOT NULL,
    longitude               DECIMAL(10,7)   NOT NULL,
    reported_at             DATETIME        NOT NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_property FOREIGN KEY (property_id) REFERENCES property(id),
    CONSTRAINT fk_incident_guard FOREIGN KEY (reported_by_guard_id) REFERENCES guard(id),
    CONSTRAINT fk_incident_shift FOREIGN KEY (shift_id) REFERENCES shift(id)
);

CREATE INDEX idx_incident_property ON incident(property_id);

-- Write-once: file_path points at a file under the app's uploads
-- directory (not committed to git, not served as static content — read
-- back only through an authenticated, scoped endpoint). Never updated or
-- deleted once created.
CREATE TABLE incident_media (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id         BIGINT          NOT NULL,
    file_path           VARCHAR(500)    NOT NULL,
    original_filename   VARCHAR(255)    NOT NULL,
    content_type        VARCHAR(100)    NOT NULL,
    file_size_bytes     BIGINT          NOT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_im_incident FOREIGN KEY (incident_id) REFERENCES incident(id)
);
