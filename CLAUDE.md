# Property Security Operations Platform — Project Context

Read this before making changes. It reflects decisions already made — don't relitigate them
without checking with the dev first.

## What this is
A digital replacement for paper-based visitor/vehicle registers at residential estates and
commercial properties, built incrementally toward a full guard/patrol/incident management
platform. See `docs/build_plan.md` for the phase order — **do not build ahead of the current
phase** even if it seems easy to add.

## Stack
- **Backend**: Spring Boot (Java), Spring Data JPA, Spring Security
- **Database**: MySQL 8+
- **Frontend**: React (web dashboard + resident portal)
- **Guard app**: React PWA for now (offline-first via service worker), not native — revisit only
  if a pilot client reports real connectivity problems
- **Auth**: phone number + OTP for residents; email/password for staff roles (guard, supervisor,
  property manager). Roles: RESIDENT, GUARD, SUPERVISOR, PROPERTY_MANAGER, CLIENT, ADMIN.

## Non-negotiable architectural rules

1. **Never hard-delete.** Every entity table uses `deleted_at` soft delete. No `DELETE FROM` in
   application code, ever.
2. **Every write to `visitor_entry`, `invitation`, or `vehicle` must also write an `audit_log`
   row** in the same transaction (entity_name, entity_id, action, before_value, after_value,
   performed_by_user_id, server-generated timestamp). See `docs/audit_trail_design.md` for the
   full reasoning — this is a legal-defensibility requirement, not a nice-to-have.
3. **The app's DB user has `INSERT` only on `audit_log`** — no `UPDATE`/`DELETE` grant. Enforce
   this at the MySQL grant level, not just in code.
4. **All timestamps are server-generated** (`NOW()` at the moment the request is received).
   Never trust a client-supplied timestamp for anything audit-relevant.
5. **Schema starting point** is `docs/property_security_schema.sql` — extend it, don't redesign
   it, unless a phase genuinely requires a structural change (flag it and explain why first).

## Conventions
- REST endpoints: `/api/v1/{resource}`, plural nouns, standard verbs.
- DTOs in/out of controllers — never expose JPA entities directly in API responses.
- Every new entity gets a Flyway migration under `src/main/resources/db/migration/`, not manual
  schema edits.
- Tests: JUnit + Spring Boot Test for services and controllers touching audit/access-control
  logic — those two areas are where correctness actually matters for the client's legal position.

## Current phase
Check `docs/build_plan.md` for the active phase and its acceptance criteria before starting work.
Ask before jumping ahead to a later phase's tables or endpoints.
