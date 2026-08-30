-- =====================================================================
-- Structural change, approved by the dev on 2026-08-30: guards no longer
-- prove they reached a patrol checkpoint by scanning a QR code. In
-- practice a driver drops guards at their post and an on-site camera
-- system already proves presence, so the QR scan step (camera or manual
-- code entry) just added friction without adding real verification.
--
-- The replacement mirrors Shift clock-in/out exactly: a guard picks a
-- checkpoint by name and taps "Check in", and the app captures GPS and
-- compares it to the checkpoint's known lat/lng within tolerance (flagged,
-- never blocked) instead of matching a scanned token. checkpoint_scan
-- rows are keyed by checkpoint_id (a normal FK) and always have been, so
-- this only removes the now-unused token column — nothing else in the
-- schema changes.
-- =====================================================================

ALTER TABLE checkpoint DROP INDEX uq_checkpoint_qr_token, DROP COLUMN qr_token;
