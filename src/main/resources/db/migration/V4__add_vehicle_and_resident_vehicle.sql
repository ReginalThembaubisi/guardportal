-- =====================================================================
-- PHASE 3 — Vehicle capture
-- Source: docs/property_security_schema.sql section 4 (vehicle,
-- resident_vehicle), taken as-is. Also completes visitor_entry per its
-- original section 5 definition: vehicle_id was deliberately omitted from
-- V3 (see that migration's header) because this table didn't exist yet —
-- added here now that it does.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 4. VEHICLES
-- ---------------------------------------------------------------------

CREATE TABLE vehicle (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration    VARCHAR(20)     NOT NULL,
    make            VARCHAR(50),
    model           VARCHAR(50),
    colour          VARCHAR(30),
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_vehicle_reg (registration)
);

-- Resident-owned vehicles get auto-recognised at entry
CREATE TABLE resident_vehicle (
    resident_id     BIGINT          NOT NULL,
    vehicle_id      BIGINT          NOT NULL,
    PRIMARY KEY (resident_id, vehicle_id),
    CONSTRAINT fk_rv_resident FOREIGN KEY (resident_id) REFERENCES resident(id),
    CONSTRAINT fk_rv_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle(id)
);

-- ---------------------------------------------------------------------
-- Complete visitor_entry with the vehicle_id column deferred from V3.
-- ---------------------------------------------------------------------

ALTER TABLE visitor_entry
    ADD COLUMN vehicle_id BIGINT NULL AFTER invitation_id,
    ADD CONSTRAINT fk_ve_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle(id),
    ADD INDEX idx_ve_vehicle (vehicle_id);
