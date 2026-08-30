-- =====================================================================
-- Structural change, approved by the dev on 2026-08-30: shift scheduling
-- (who's working which property, day or night, and when) has been
-- happening entirely outside the app over a WhatsApp group. This adds a
-- shift_schedule table so a Supervisor can upload/enter that roster
-- directly, and guards can see their own upcoming shifts in the guard
-- app instead of relying on a chat thread.
--
-- Deliberately a separate table from `shift`, not a merge into it: a
-- schedule is a plan (created ahead of time, editable, cancellable);
-- `shift` is what actually happened (append-only, clocked in/out). Same
-- config/master-data shape as `checkpoint`/`patrol_route` — extends
-- BaseEntity (soft-deletable) rather than being append-only.
--
-- shift.shift_type is added alongside it so the actual worked shift also
-- records day/night — populated from the matching shift_schedule row
-- when one exists at clock-in time, otherwise derived from the clock-in
-- hour. Existing shift rows get NULL, which is fine: it's informational
-- only, never used in access-control or tolerance logic.
-- =====================================================================

CREATE TABLE shift_schedule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    guard_id        BIGINT          NOT NULL,
    property_id     BIGINT          NOT NULL,
    shift_date      DATE            NOT NULL,
    shift_type      VARCHAR(10)     NOT NULL,
    start_time      TIME            NULL,
    end_time        TIME            NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL,
    CONSTRAINT fk_shift_schedule_guard FOREIGN KEY (guard_id) REFERENCES guard(id),
    CONSTRAINT fk_shift_schedule_property FOREIGN KEY (property_id) REFERENCES property(id)
);

-- Speeds up "does this guard have a scheduled shift today" (checked on
-- every clock-in) and the guard's own "my upcoming shifts" list.
CREATE INDEX idx_shift_schedule_guard_date ON shift_schedule(guard_id, shift_date);

-- Speeds up a Supervisor's roster view for one property.
CREATE INDEX idx_shift_schedule_property_date ON shift_schedule(property_id, shift_date);

ALTER TABLE shift ADD COLUMN shift_type VARCHAR(10) NULL AFTER clock_out_within_tolerance;
