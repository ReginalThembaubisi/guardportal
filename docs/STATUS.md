# Known Gaps and Deferred Work

## Pre-fix timezone rows — data caveat (2026-09-01)

`clock_in_at` and `clock_out_at` rows written before the timezone fix (commit 818257b) are
JVM-local time; rows after are property-local (Africa/Johannesburg, UTC+2). On a dev machine
running SAST these are identical. On a UTC cloud host they diverge by two hours, with no
column distinguishing which convention applies. Cannot be repaired by inspection after the fact.
Action before any non-dev deployment: confirm whether the target host ran in UTC before the fix;
if so, identify the affected rows (by `created_at` < fix deploy time) and decide whether to
offset them or leave them as a known anomaly. If the fix deploys before any non-dev data is
written, no action needed.

## Shift list — resolved (commits A + B, 2026-09-01)

`clock_out_source` is now readable. `GET /api/v1/shifts?propertyId=` (SUPERVISOR + ADMIN) serves
all four states; the resident-dashboard `/shifts` page surfaces them with the correct visual
treatment (`--flag` dashed seal for `CLIENT_CLAIMED_LATE` and `ROSTER_AUTO_CLOSED`, `--danger`
left-border for never-closed open shifts). The roster auto-close job (`ROSTER_AUTO_CLOSED`) runs
every 15 min at rostered end + 90 min grace.
