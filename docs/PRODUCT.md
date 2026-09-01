# Product Overview

A digital replacement for paper-based visitor/vehicle registers at residential estates
and commercial properties, built toward a full guard/patrol/incident management platform.

---

## Capabilities and Constraints

**Visitor and vehicle management**
Residents generate short-code invitations shared via any messaging app. Guards check
visitors in at the gate by code (no QR scanner required) or via walk-in manual entry.
Every entry creates an audit record; no record can be hard-deleted.

**Guard shift clock-in/out with GPS coordinates captured as evidence — never scored
against a radius and never blocked.** Guards are dropped at the property and clock in
on arrival; the coordinate is the record.

**Patrol checkpoints and routes, GPS-flagged scans.** A checkpoint scan's purpose is
proving the guard walked to the location. Distance is compared against a per-checkpoint
(or property-fallback) radius; out-of-range scans are flagged, never blocked.

**Offline-first guard app.** Writes queue to IndexedDB when the device is offline.
The server deduplicates via an idempotency filter. Clock-out stays visually "ending"
until the server confirms. Queue entries older than 72 hours can still be submitted
and are marked CLIENT_CLAIMED_LATE on the record.

**Roster-based shift auto-close.** A 15-minute background job closes shifts that are
still open 90 minutes past the rostered end time, setting clock_out_source to
ROSTER_AUTO_CLOSED. Night-shift midnight-crossing is handled correctly. Every
auto-closed shift is visible on the supervisor shift list and coverage report.

**No push notifications, no SMS, no live approve/deny flows.** All coordination
happens through the same messaging apps guards and residents already use.

---

## Roles

| Role             | Access                                                                 |
|------------------|------------------------------------------------------------------------|
| GUARD            | Clock in/out, gate check-in, checkpoint scans, incident reports        |
| SUPERVISOR       | Shift list, coverage report, roster management, patrol status          |
| PROPERTY_MANAGER | Same as SUPERVISOR plus occupancy dashboard, resident list             |
| RESIDENT         | Invitations, visit history, vehicle registration                       |
| ADMIN            | All of the above plus property/unit/staff management, audit log        |

---

## Design rules

- **Flag anomalies, never block.** An out-of-range scan, a late clock-out, an
  auto-closed shift — all are recorded and surfaced, never used to refuse a write.
- **Dashed seals mean unconfirmed** in every app surface.
- **Unconfirmed uses the flag colour; never the danger colour.** Danger is reserved
  for shifts that never closed at all.
- **Both client and server timestamps are kept.** The server receipt time goes into
  the audit chain. The client-claimed time is shown only when the delta exceeds 2
  minutes (a skewed phone clock is an anomaly worth surfacing, not a reason to reject).
- **No hard deletes.** Every table uses `deleted_at`. The DB user has INSERT-only
  on `audit_log`.
