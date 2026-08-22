-- =====================================================================
-- Not in docs/property_security_schema.sql — a genuine structural
-- addition, approved by the dev on 2026-08-22 per CLAUDE.md's "flag and
-- explain why first" rule, and (like V6) an explicit override of
-- build_plan.md's "don't start Phase 5 before pilot feedback" note.
--
-- Why: patrol checkpoints (Phase 5) need a place per property to define
-- named, GPS-located points a guard proves they visited, ordered routes
-- of those points, and a log of scans against them.
-- =====================================================================

-- Config/master data, like guard or property_manager — extends BaseEntity
-- (soft-deletable) rather than being append-only like shift/visitor_entry.
CREATE TABLE checkpoint (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id             BIGINT          NOT NULL,
    name                    VARCHAR(150)    NOT NULL,
    latitude                DECIMAL(10,7)   NOT NULL,
    longitude               DECIMAL(10,7)   NOT NULL,
    -- Nullable — falls back to property.geo_tolerance_meters, then
    -- app.geo.default-tolerance-meters. Checkpoints are far more localized
    -- than a whole property (e.g. one indoors with poor GPS reception vs.
    -- one in an open yard), so a single property-wide radius is a worse
    -- fit here than it is for shift clock-in.
    geo_tolerance_meters    INT             NULL,
    qr_token                VARCHAR(36)     NOT NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              DATETIME        NULL,
    CONSTRAINT fk_checkpoint_property FOREIGN KEY (property_id) REFERENCES property(id),
    UNIQUE KEY uq_checkpoint_qr_token (qr_token)
);

CREATE TABLE patrol_route (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id     BIGINT          NOT NULL,
    name            VARCHAR(150)    NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL,
    CONSTRAINT fk_route_property FOREIGN KEY (property_id) REFERENCES property(id)
);

-- Ordered many-to-many: a checkpoint can appear on more than one route
-- (e.g. a shared gate on both a "full" and a "quick" patrol). Composition
-- data, not its own audited entity — no deleted_at, same as app_user_role.
-- Written once at route-creation time; nothing in this phase edits a
-- route afterwards, so no UPDATE/DELETE grant is needed on this table.
CREATE TABLE patrol_route_checkpoint (
    route_id        BIGINT      NOT NULL,
    checkpoint_id   BIGINT      NOT NULL,
    sequence_order  INT         NOT NULL,
    PRIMARY KEY (route_id, checkpoint_id),
    CONSTRAINT fk_prc_route FOREIGN KEY (route_id) REFERENCES patrol_route(id),
    CONSTRAINT fk_prc_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES checkpoint(id),
    UNIQUE KEY uq_route_sequence (route_id, sequence_order)
);

-- Append-only proof-of-presence log, same reasoning as visitor_entry/shift:
-- never soft-deleted. guard_id is denormalized off shift_id (like
-- visitor_entry.processed_by_guard_id is off invitation) purely so the
-- missed-checkpoint view doesn't need to join through shift for it.
CREATE TABLE checkpoint_scan (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    checkpoint_id           BIGINT          NOT NULL,
    shift_id                BIGINT          NOT NULL,
    guard_id                BIGINT          NOT NULL,
    scanned_at              DATETIME        NOT NULL,
    latitude                DECIMAL(10,7)   NOT NULL,
    longitude               DECIMAL(10,7)   NOT NULL,
    distance_meters         INT             NULL,
    within_tolerance        TINYINT(1)      NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scan_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES checkpoint(id),
    CONSTRAINT fk_scan_shift FOREIGN KEY (shift_id) REFERENCES shift(id),
    CONSTRAINT fk_scan_guard FOREIGN KEY (guard_id) REFERENCES guard(id)
);

-- Speeds up the missed-checkpoint view: for a given checkpoint, all scans
-- within a time window.
CREATE INDEX idx_scan_checkpoint_time ON checkpoint_scan(checkpoint_id, scanned_at);
