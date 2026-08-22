-- =====================================================================
-- Not in docs/property_security_schema.sql — a genuine structural
-- addition, approved by the dev on 2026-08-22 per CLAUDE.md's "flag and
-- explain why first" rule for schema changes beyond the sketch.
--
-- Why: Phase 4's own "done when" criteria requires a working property
-- manager dashboard, but the schema has no way to associate a
-- PROPERTY_MANAGER-role app_user with the property(ies) they manage —
-- the same gap flagged (and worked around by exclusion) in Phase 1,
-- Phase 3's vehicle history, and Phase 4's occupancy/exit endpoints.
-- This table closes it, mirroring `guard`'s shape but allowing a manager
-- to be linked to more than one property (multiple rows per user_id),
-- unlike guard's one-guard-one-property design.
-- =====================================================================

CREATE TABLE property_manager (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    property_id     BIGINT          NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_pm_property FOREIGN KEY (property_id) REFERENCES property(id),
    UNIQUE KEY uq_pm_user_property (user_id, property_id)
);
