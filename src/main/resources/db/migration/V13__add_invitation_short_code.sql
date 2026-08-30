-- =====================================================================
-- Code-first check-in: invitation.qr_token is a 36-char UUID, unusable
-- as something a guard types manually. short_code is a 6-digit
-- SecureRandom code, displayed/spoken as two triples ("417 302"), meant
-- to be typed at the gate with the QR still available as a scan.
--
-- Uniqueness is deliberately NOT a DB constraint: the requirement is
-- scoped (unique among invitations at the same property whose validity
-- window overlaps and which are still PENDING), which is a temporal
-- condition a plain unique index can't express. It's enforced in
-- InvitationService at generation time instead, with regenerate-on-
-- collision. Old, no-longer-overlapping invitations are free to reuse a
-- digit combination — check-in resolves a short code by taking the most
-- recently created match at the guard's property, which is what makes
-- that reuse safe.
--
-- Existing rows get a random backfilled code (nobody types a code for an
-- invitation created before this migration ran, so the exact value is a
-- placeholder — just needs to satisfy NOT NULL).
-- =====================================================================

ALTER TABLE invitation ADD COLUMN short_code CHAR(6) NULL AFTER qr_token;

UPDATE invitation SET short_code = LPAD(FLOOR(RAND() * 1000000), 6, '0') WHERE short_code IS NULL;

ALTER TABLE invitation MODIFY COLUMN short_code CHAR(6) NOT NULL;

CREATE INDEX idx_invitation_short_code ON invitation (short_code);

-- ---------------------------------------------------------------------
-- Failed short-code lookup attempts — brute-force visibility, not a
-- legal audit trail. Deliberately a separate, plain table rather than a
-- widening of audit_log: audit_log is hash-chained and schema-locked to
-- real entity writes (entity_id NOT NULL, a closed CREATE/UPDATE/
-- SOFT_DELETE action set — see V2__add_audit_log.sql), and a failed
-- lookup creates no entity to attach a row to. The attempted code itself
-- is not stored — this is for volume/pattern visibility (is one guard's
-- token being hammered), not per-guess forensics.
-- ---------------------------------------------------------------------

CREATE TABLE short_code_lookup_attempt (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    guard_id        BIGINT      NOT NULL,
    property_id     BIGINT      NOT NULL,
    reason          ENUM('EXPIRED','NOT_YET_VALID','ALREADY_USED','NOT_FOUND') NOT NULL,
    attempted_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scla_guard FOREIGN KEY (guard_id) REFERENCES guard(id),
    CONSTRAINT fk_scla_property FOREIGN KEY (property_id) REFERENCES property(id),
    INDEX idx_scla_guard_time (guard_id, attempted_at)
);
