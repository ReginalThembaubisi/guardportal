-- =====================================================================
-- Approved by the dev on 2026-08-31 per CLAUDE.md "flag and explain why".
--
-- Part 1: client_claimed_at fields.
-- Every write type that the offline queue can buffer now carries two
-- timestamps: the guard's phone clock at submit time (client_claimed_at,
-- provided by the client) and the server's receipt clock (existing fields,
-- e.g. entered_at / clock_in_at). Both are kept; only the server's stamp
-- is in the audit chain. When the delta exceeds 2 minutes the UI shows
-- the claimed time as primary — the visitor didn't arrive at 07:40 if the
-- guard checked them in at 23:09 offline. See frame 2a in the design file.
--
-- Part 2: idempotency_key table.
-- Every buffered write carries a client-generated UUID (Idempotency-Key
-- header). The server inserts the key before processing and finalizes it
-- after. A key already in the table returns the cached response; a key
-- that is still in_flight beyond 120 seconds is reclaimable (JVM crash
-- or response-write failure — see IdempotencyFilter). Unique on idem_key
-- alone: the same UUID hitting two endpoints is a client bug surfaced as
-- 422, not silently permitted (see scope doc).
--
-- Retention is 7 days (IdempotencyCleanupService). A phone offline over
-- a weekend is ordinary; a 24-hour TTL would turn that into a silent
-- duplicate on sync.
-- =====================================================================

-- Part 1a: shift — two events, so two columns (see naming note).
ALTER TABLE shift
    ADD COLUMN client_claimed_clock_in_at  DATETIME NULL AFTER clock_in_at,
    ADD COLUMN client_claimed_clock_out_at DATETIME NULL AFTER clock_out_at;

-- Part 1b: visitor_entry
ALTER TABLE visitor_entry
    ADD COLUMN client_claimed_at DATETIME NULL AFTER entered_at;

-- Part 1c: checkpoint_scan
ALTER TABLE checkpoint_scan
    ADD COLUMN client_claimed_at DATETIME NULL AFTER scanned_at;

-- Part 1d: incident
ALTER TABLE incident
    ADD COLUMN client_claimed_at DATETIME NULL AFTER reported_at;

-- Part 2: idempotency_key
CREATE TABLE idempotency_key (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    idem_key       VARCHAR(36)     NOT NULL,
    endpoint       VARCHAR(150)    NOT NULL,
    principal_id   BIGINT          NOT NULL,
    in_flight      TINYINT(1)      NOT NULL DEFAULT 1,
    status_code    SMALLINT        NULL,
    response_body  MEDIUMTEXT      NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_idem_key (idem_key)
);

-- Covers cleanup scan (delete by created_at range).
CREATE INDEX idx_idem_created ON idempotency_key(created_at);
