-- =====================================================================
-- One-time DBA step, NOT run by Flyway or the app.
--
-- Run this as root/admin, once per environment, after the audit_log table
-- exists (V2__add_audit_log.sql). Re-run the UPDATE/DELETE grant block
-- below whenever a new table is added by a later migration — MySQL/MariaDB
-- privileges are additive, so a database-level GRANT ALL cannot be
-- selectively revoked on one table; the only way to actually keep
-- audit_log off UPDATE/DELETE is to never grant those two at the database
-- level and instead grant them per table everywhere except audit_log.
--
-- Why not in the migration: the app's own DB user runs the Flyway
-- migrations, and it deliberately has no GRANT OPTION, so it cannot
-- change its own privileges. This has to come from an account with admin
-- rights on the schema.
--
-- Effect: the app's DB user gets SELECT/INSERT and DDL (CREATE/ALTER/
-- INDEX/REFERENCES/DROP, needed for Flyway) database-wide, but UPDATE and
-- DELETE only on the tables that legitimately need them. audit_log ends
-- up with INSERT + SELECT only (SELECT because AuditLogService.verifyChain
-- reads the whole table to walk the hash chain) — no UPDATE, no DELETE,
-- so even a bug in the app or a compromised app-layer credential cannot
-- alter or erase audit history. Enforced by MySQL itself, per
-- docs/audit_trail_design.md point 2.
--
-- Adjust the username/host below to match your environment (this repo's
-- local dev setup uses 'property_security_app'@'localhost' and
-- 'property_security_app'@'127.0.0.1').
-- =====================================================================

-- Reset to a clean slate, then rebuild the privilege set explicitly.
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'property_security_app'@'localhost';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'property_security_app'@'127.0.0.1';

-- Database-wide: read/write rows in general, plus DDL for Flyway to
-- create/alter tables (including ones added by future migrations).
-- Deliberately excludes UPDATE and DELETE.
GRANT SELECT, INSERT, CREATE, ALTER, INDEX, REFERENCES, DROP
    ON property_security_platform.* TO 'property_security_app'@'localhost';
GRANT SELECT, INSERT, CREATE, ALTER, INDEX, REFERENCES, DROP
    ON property_security_platform.* TO 'property_security_app'@'127.0.0.1';

-- Per-table UPDATE/DELETE for every table except audit_log. Add a line
-- here for each new table a future migration introduces.
GRANT UPDATE, DELETE ON property_security_platform.property TO 'property_security_app'@'localhost';
GRANT UPDATE, DELETE ON property_security_platform.unit TO 'property_security_app'@'localhost';
GRANT UPDATE, DELETE ON property_security_platform.app_user TO 'property_security_app'@'localhost';
GRANT UPDATE, DELETE ON property_security_platform.app_user_role TO 'property_security_app'@'localhost';
GRANT UPDATE, DELETE ON property_security_platform.resident TO 'property_security_app'@'localhost';
GRANT UPDATE, DELETE ON property_security_platform.guard TO 'property_security_app'@'localhost';
GRANT UPDATE, DELETE ON property_security_platform.otp_verification TO 'property_security_app'@'localhost';
-- Phase 2: invitation.status transitions PENDING -> USED on a successful scan.
GRANT UPDATE, DELETE ON property_security_platform.invitation TO 'property_security_app'@'localhost';
-- Phase 4: the exit flow sets exited_at/exit_processed_by_guard_id. UPDATE
-- only, deliberately no DELETE — visitor_entry is the paper-register
-- replacement, an append-only log that's never deleted (see the VisitorEntry
-- entity's own docstring).
GRANT UPDATE ON property_security_platform.visitor_entry TO 'property_security_app'@'localhost';
-- Phase 5: clock-out sets clock_out_at/latitude/longitude/distance/tolerance
-- on the row the clock-in created. UPDATE only, no DELETE — same append-mostly
-- reasoning as visitor_entry (see the Shift entity's own docstring).
GRANT UPDATE ON property_security_platform.shift TO 'property_security_app'@'localhost';

GRANT UPDATE, DELETE ON property_security_platform.property TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON property_security_platform.unit TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON property_security_platform.app_user TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON property_security_platform.app_user_role TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON property_security_platform.resident TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON property_security_platform.guard TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON property_security_platform.otp_verification TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON property_security_platform.invitation TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE ON property_security_platform.visitor_entry TO 'property_security_app'@'127.0.0.1';
GRANT UPDATE ON property_security_platform.shift TO 'property_security_app'@'127.0.0.1';

FLUSH PRIVILEGES;

-- Verify: should show SELECT/INSERT/CREATE/ALTER/INDEX/REFERENCES/DROP at
-- the database level, and UPDATE/DELETE only on individual named tables
-- (never audit_log).
-- SHOW GRANTS FOR 'property_security_app'@'localhost';
