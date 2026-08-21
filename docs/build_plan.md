# Build Plan — Property Security Operations Platform

Work through these phases in order. Each has a clear "done" line — don't move to the next phase
until the current one meets it. Give Claude Code one phase at a time as a task, not the whole plan
at once, so it isn't tempted to shortcut ahead.

---

## Phase 1 — Core entities, auth, roles
**Build:**
- `property`, `unit`, `app_user`, `app_user_role`, `resident`, `guard` tables (Flyway migration
  from `property_security_schema.sql`)
- Spring Security setup with the 6 roles
- Phone+OTP login for residents, email/password for staff roles
- Basic CRUD API for property/unit (admin only) and resident/guard (property manager only)

**Done when:** you can create a property, add units, add a resident to a unit, add a guard to a
property, and log in as each role with the correct permissions enforced.

---

## Phase 2 — Visitor invitations + QR check-in
**Build:**
- `invitation` table + API: resident creates invitation, gets back a QR token
- QR code generation (server-side, ZXing) encoding a check-in URL
- WhatsApp share link (`wa.me` prefilled message)
- Guard-facing scan endpoint: validates token, time window, status
- `visitor_entry` created on successful scan
- Audit log writes on invitation create/use and visitor_entry create

**Done when:** a resident can create an invite from the portal, share it via WhatsApp, and a
guard can scan it (or manually look it up) and check the visitor in — with an audit_log row
proving it happened.

---

## Phase 3 — Vehicle capture
**Build:**
- `vehicle`, `resident_vehicle` tables
- Vehicle capture on visitor_entry (manual entry first — plate recognition is a later
  integration, not phase 3)
- Auto-recognition: if a scanned/entered registration matches a `resident_vehicle`, flag it
- Vehicle history view: all entries for a given registration

**Done when:** every visitor_entry can optionally carry a vehicle, and you can search "show me
every time this registration has been on the property."

---

## Phase 4 — Live occupancy + resident/property manager dashboards
**Build:**
- `GET /properties/{id}/occupancy` — everyone currently on site (visitor_entry where
  exited_at IS NULL), grouped by category
- Exit flow: guard checks a visitor out, exited_at is server-stamped
- Resident portal: visitor history view
- Property manager dashboard: live occupancy + recent activity

**Done when:** this is a demo-able pilot. A property manager can open a dashboard and see who's
on site right now, and a resident can see their own visitor history. This is the point where you
take it to a real client.

---

## Phase 5+ (not detailed yet — revisit after pilot feedback)
- Guard clock-in/out with GPS
- Patrol checkpoints
- Incident management + evidence-pack export (see `docs/audit_trail_design.md`)
- Client dashboard aggregating across the above
- Audit log hash-chaining + DB permission lockdown (do this one early regardless — see notes
  in `docs/audit_trail_design.md` on what's cheap enough to do immediately)

Do not start Phase 5 work until Phase 1-4 have been used by a real pilot property. Feedback from
actual guards and residents will change some of these before they're worth building.
