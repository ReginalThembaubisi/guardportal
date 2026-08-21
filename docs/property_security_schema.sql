-- =====================================================================
-- PROPERTY SECURITY OPERATIONS PLATFORM — PHASE 1-4 SCHEMA SKETCH
-- Covers: core entities, auth/roles, visitor invitations + QR check-in,
-- vehicle capture, live occupancy, and the audit trail.
-- Target: MySQL 8+, Spring Boot / Spring Data JPA
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. CORE PROPERTY STRUCTURE
-- ---------------------------------------------------------------------

CREATE TABLE property (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(150)    NOT NULL,
    address         VARCHAR(255),
    timezone        VARCHAR(50)     NOT NULL DEFAULT 'Africa/Johannesburg',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL          -- soft delete, never hard delete
);

CREATE TABLE unit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id     BIGINT          NOT NULL,
    unit_number     VARCHAR(30)     NOT NULL,     -- e.g. "42" or "Erf 16"
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL,
    CONSTRAINT fk_unit_property FOREIGN KEY (property_id) REFERENCES property(id),
    UNIQUE KEY uq_unit_per_property (property_id, unit_number)
);

-- ---------------------------------------------------------------------
-- 2. USERS & ROLES
-- One users table, role-based via app_user_role. Keeps Spring Security
-- clean: @PreAuthorize("hasRole('GUARD')") etc.
-- ---------------------------------------------------------------------

CREATE TABLE app_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(150)    NOT NULL,
    phone_number    VARCHAR(20)     NOT NULL UNIQUE,
    email           VARCHAR(150)    UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL
);

CREATE TABLE app_user_role (
    user_id         BIGINT          NOT NULL,
    role            ENUM('RESIDENT','GUARD','SUPERVISOR','PROPERTY_MANAGER','CLIENT','ADMIN') NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_role_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

-- Residents are users linked to a specific unit
CREATE TABLE resident (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    unit_id         BIGINT          NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL,
    CONSTRAINT fk_resident_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_resident_unit FOREIGN KEY (unit_id) REFERENCES unit(id)
);

-- Guards are users linked to a property (their assigned site)
CREATE TABLE guard (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    property_id     BIGINT          NOT NULL,
    badge_number    VARCHAR(30),
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME        NULL,
    CONSTRAINT fk_guard_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_guard_property FOREIGN KEY (property_id) REFERENCES property(id)
);

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
-- 5. VISITOR ENTRY — the core transactional record.
-- This single table drives the paper-register replacement AND the
-- live occupancy dashboard (query: entries where exited_at IS NULL).
-- ---------------------------------------------------------------------

CREATE TABLE visitor_entry (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id         BIGINT          NOT NULL,
    unit_id             BIGINT          NULL,          -- destination unit, if known
    invitation_id        BIGINT          NULL,          -- NULL = unexpected visitor
    vehicle_id          BIGINT          NULL,
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
    CONSTRAINT fk_ve_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle(id),
    CONSTRAINT fk_ve_guard_in FOREIGN KEY (processed_by_guard_id) REFERENCES guard(id),
    CONSTRAINT fk_ve_guard_out FOREIGN KEY (exit_processed_by_guard_id) REFERENCES guard(id),
    INDEX idx_ve_property_open (property_id, exited_at),   -- powers the occupancy dashboard
    INDEX idx_ve_vehicle (vehicle_id)
);

-- ---------------------------------------------------------------------
-- 6. AUDIT LOG — write to this from a service-layer interceptor,
-- not from triggers. Every create/update on visitor_entry, invitation,
-- and vehicle should produce a row here. Never update or delete rows
-- in this table from application code.
-- ---------------------------------------------------------------------

CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_name     VARCHAR(50)     NOT NULL,     -- e.g. "visitor_entry"
    entity_id       BIGINT          NOT NULL,
    action          ENUM('CREATE','UPDATE','SOFT_DELETE') NOT NULL,
    performed_by_user_id BIGINT      NULL,
    before_value    JSON            NULL,
    after_value     JSON            NULL,
    performed_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_entity (entity_name, entity_id)
);

-- =====================================================================
-- NOTES FOR PHASE 5+ (not in this sketch, add when you get there):
--   - shift, clock_in_out (guard GPS clock-in)
--   - checkpoint, patrol, checkpoint_scan
--   - incident, incident_media
--   - client_dashboard is just read queries/views over the above —
--     no new core tables needed until you add per-client scoping.
-- =====================================================================
