-- =====================================================================
-- PHASE 1 — Core entities, auth, roles
-- Source: docs/property_security_schema.sql, sections 1 (core property
-- structure) and 2 (users & roles), taken verbatim.
--
-- Added beyond the schema sketch: otp_verification, needed for phone+OTP
-- resident login (Phase 1 requirement) but not covered by the sketch.
-- Flagged to and approved by the dev before adding.
--
-- Not created yet (later phases, per docs/build_plan.md):
--   invitation, vehicle, resident_vehicle, visitor_entry, audit_log
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
-- 3. OTP VERIFICATION (not in property_security_schema.sql — added for
-- Phase 1 resident phone+OTP login, approved by dev on 2026-08-21)
-- One row per requested code. Old rows are left in place (never
-- hard-deleted) as a record of login attempts.
-- ---------------------------------------------------------------------

CREATE TABLE otp_verification (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone_number    VARCHAR(20)     NOT NULL,
    otp_code_hash   VARCHAR(255)    NOT NULL,
    expires_at      DATETIME        NOT NULL,
    attempt_count   INT             NOT NULL DEFAULT 0,
    consumed_at     DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_otp_phone (phone_number)
);
