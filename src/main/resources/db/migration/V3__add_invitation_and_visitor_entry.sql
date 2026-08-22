-- =====================================================================
-- PHASE 2 — Visitor invitations + QR check-in
-- Source: docs/property_security_schema.sql sections 3 (invitation) and 5
-- (visitor_entry), taken as-is with one deliberate deviation, approved by
-- the dev on 2026-08-22:
--
-- visitor_entry.vehicle_id (FK to vehicle) is omitted here. The `vehicle`
-- table doesn't exist until Phase 3, so that FK can't be created yet.
-- Phase 3's migration will ALTER TABLE visitor_entry to add vehicle_id
-- and its FK once `vehicle` exists.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 3. VISITOR INVITATIONS (resident pre-authorisation + QR)
-- ---------------------------------------------------------------------

CREATE TABLE invitation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    resident_id         BIGINT          NOT NULL,
    visitor_name        VARCHAR(150)    NOT NULL,
    visitor_phone       VARCHAR(20),
    expected_vehicle_reg VARCHAR(20),
    purpose             VARCHAR(255),
    valid_from          DATETIME        NOT NULL,
    valid_until         DATETIME        NOT NULL,
    qr_token            CHAR(36)        NOT NULL UNIQUE,   -- UUID, put in the QR code
    status              ENUM('PENDING','USED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invitation_resident FOREIGN KEY (resident_id) REFERENCES resident(id),
    INDEX idx_invitation_token (qr_token)
);

-- ---------------------------------------------------------------------
-- 5. VISITOR ENTRY — the core transactional record.
-- This single table drives the paper-register replacement AND the
-- live occupancy dashboard (query: entries where exited_at IS NULL).
-- (vehicle_id intentionally omitted — see header note above.)
-- ---------------------------------------------------------------------

CREATE TABLE visitor_entry (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id         BIGINT          NOT NULL,
    unit_id             BIGINT          NULL,          -- destination unit, if known
    invitation_id        BIGINT          NULL,          -- NULL = unexpected visitor
    visitor_name        VARCHAR(150)    NOT NULL,
    visitor_id_number   VARCHAR(30),                    -- ID/passport, if captured
    visitor_phone       VARCHAR(20),
    category             ENUM('VISITOR','CONTRACTOR','DELIVERY','STAFF') NOT NULL DEFAULT 'VISITOR',
    processed_by_guard_id BIGINT        NOT NULL,        -- entry guard
    entered_at          DATETIME        NOT NULL,
    exited_at            DATETIME        NULL,
    exit_processed_by_guard_id BIGINT   NULL,
    approval_status      ENUM('AUTO_APPROVED','RESIDENT_APPROVED','DENIED','PENDING') NOT NULL,
    notes                VARCHAR(500),
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ve_property FOREIGN KEY (property_id) REFERENCES property(id),
    CONSTRAINT fk_ve_unit FOREIGN KEY (unit_id) REFERENCES unit(id),
    CONSTRAINT fk_ve_invitation FOREIGN KEY (invitation_id) REFERENCES invitation(id),
    CONSTRAINT fk_ve_guard_in FOREIGN KEY (processed_by_guard_id) REFERENCES guard(id),
    CONSTRAINT fk_ve_guard_out FOREIGN KEY (exit_processed_by_guard_id) REFERENCES guard(id),
    INDEX idx_ve_property_open (property_id, exited_at)
);
