-- =====================================================================
-- Not in docs/property_security_schema.sql — a genuine structural
-- addition, approved by the dev on 2026-08-24 per CLAUDE.md's "flag and
-- explain why first" rule, and (like V6/V7/V8) an explicit override of
-- build_plan.md's "Client dashboard" being listed under Phase 5+ ("not
-- detailed yet — revisit after pilot feedback").
--
-- Why: the property owner (CLIENT) had no way to self-serve manage their
-- own resident roster — only PROPERTY_MANAGER/ADMIN could. CLIENT existed
-- as a role label only, with nothing linking a client account to the
-- property they actually own (unlike guard/property_manager/
-- property_supervisor, which all already have that link). This closes
-- that gap the same way property_supervisor closed it for SUPERVISOR.
-- =====================================================================

-- Mirrors property_manager exactly — see that table's own migration
-- comment (V5) for the reasoning. A client can own more than one property.
CREATE TABLE property_client (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    property_id     BIGINT          NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL,
    CONSTRAINT fk_pc_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_pc_property FOREIGN KEY (property_id) REFERENCES property(id),
    UNIQUE KEY uq_pc_user_property (user_id, property_id)
);
