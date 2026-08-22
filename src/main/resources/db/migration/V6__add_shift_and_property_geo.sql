-- =====================================================================
-- Not in docs/property_security_schema.sql — a genuine structural
-- addition, approved by the dev on 2026-08-22 per CLAUDE.md's "flag and
-- explain why first" rule for schema changes beyond the sketch. This also
-- overrides build_plan.md's "don't start Phase 5 before pilot feedback"
-- note, at the dev's explicit request — see that file for the note this
-- migration corresponds to.
--
-- Why: Phase 5 guard clock-in/out needs somewhere to compare a guard's
-- captured GPS position against, and the property table has no notion of
-- a physical location at all (only a free-text address). geo_tolerance_meters
-- is nullable and per-property (falls back to app.geo.default-tolerance-meters
-- in application.yml when unset) because estate sizes vary enormously — a
-- small block and a large complex shouldn't share one fixed radius.
-- =====================================================================

ALTER TABLE property
    ADD COLUMN latitude  DECIMAL(10,7) NULL AFTER address,
    ADD COLUMN longitude DECIMAL(10,7) NULL AFTER latitude,
    ADD COLUMN geo_tolerance_meters INT NULL AFTER longitude;

-- Not a BaseEntity-style soft-deletable table — like visitor_entry,
-- invitation, and vehicle, this is an append-mostly transactional record
-- (a shift is clocked in, then later clocked out; never removed).
CREATE TABLE shift (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    guard_id                    BIGINT          NOT NULL,
    property_id                 BIGINT          NOT NULL,

    clock_in_at                 DATETIME        NOT NULL,
    clock_in_latitude           DECIMAL(10,7)   NOT NULL,
    clock_in_longitude          DECIMAL(10,7)   NOT NULL,
    -- NULL when the property has no known lat/lng yet to compare against —
    -- this is a first-pass flag, not a hardened anti-fraud system, so an
    -- unverifiable clock-in is left unflagged rather than blocked.
    clock_in_distance_meters    INT             NULL,
    clock_in_within_tolerance   TINYINT(1)      NULL,

    clock_out_at                DATETIME        NULL,
    clock_out_latitude          DECIMAL(10,7)   NULL,
    clock_out_longitude         DECIMAL(10,7)   NULL,
    clock_out_distance_meters   INT             NULL,
    clock_out_within_tolerance  TINYINT(1)      NULL,

    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shift_guard FOREIGN KEY (guard_id) REFERENCES guard(id),
    CONSTRAINT fk_shift_property FOREIGN KEY (property_id) REFERENCES property(id)
);

-- Speeds up "does this guard already have an open shift" (guard_id, clock_out_at IS NULL),
-- checked on every clock-in.
CREATE INDEX idx_shift_guard_open ON shift(guard_id, clock_out_at);
