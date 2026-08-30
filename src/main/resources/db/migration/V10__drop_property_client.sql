-- =====================================================================
-- Structural change, approved by the dev on 2026-08-28: CLIENT is being
-- retired as a distinct role in this system. V9's premise -- that the
-- property owner needs their own self-serve resident-roster screen,
-- separate from PROPERTY_MANAGER -- was wrong: in practice that roster
-- work IS the property manager's job, not the owner's. That
-- functionality has been moved onto PROPERTY_MANAGER directly (see the
-- same commit that adds this migration), so the property_client link
-- table this feature depended on is now dead.
--
-- The CLIENT value stays in app_user_role's enum (a handful of existing
-- dev/test rows still carry it, and it's cheap to leave unused rather
-- than force an enum-narrowing migration for a value nothing reads
-- anymore) -- only the link table backing its former screen is dropped.
-- =====================================================================

DROP TABLE property_client;
