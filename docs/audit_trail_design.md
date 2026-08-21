# Audit Trail & Legal Defensibility — Design Notes
Property Security Operations Platform

The proposal promises "legal-grade documentation" and "court-admissible incident records."
That claim only holds if the audit trail is engineered to prove **integrity** (nothing was altered)
and **reliability** (the record was created honestly, by an identifiable party, at the time claimed).
A plain `audit_log` table that's just a history log is not enough on its own — the points below
are what turn it into something a client can actually rely on.

---

## 1. Hash-chain the audit log

Each audit row's hash depends on the row before it, so any later edit or deletion breaks the chain.

```
audit_log.record_hash = SHA256(
    previous_row.record_hash
    + entity_name + entity_id + action
    + before_value + after_value + performed_at
)
```

- No blockchain infrastructure needed — this is a single extra column plus a hash computed at write time.
- Build a verification job / endpoint (`GET /audit/verify`) that recomputes the chain and flags
  the first row where it breaks. This is your proof of integrity when someone asks
  "how do we know this record wasn't changed?"

## 2. Lock the audit table down at the database level

- The app's DB user should have `INSERT` only on `audit_log` — **no `UPDATE` or `DELETE` grant**,
  enforced by MySQL itself, not just application code.
- This protects the trail even from a bug in your own app or a compromised admin account.
- Add a periodic export: nightly job pushes `audit_log` to write-once storage (e.g. S3 with
  Object Lock, or a signed file somewhere the app itself can't reach). Gives you an off-system
  copy to compare against if the live database is ever disputed.

## 3. Timestamps must always come from the server

- Never trust a timestamp sent from a guard's phone or a resident's browser — for
  `visitor_entry.entered_at`, checkpoint scans, incident reports, all of it.
- Always stamp with the server's `NOW()` the moment the request is received.
- Client-side clocks are trivially spoofable and would be the first thing challenged.

## 4. Build an evidence-pack export, not just raw table access

When an incident actually needs to go to a lawyer, insurer, or police, what's needed is a
coherent bundle, not a database query:

- Incident report + linked visitor entry + vehicle record + guard identity
- GPS coordinates and timestamps
- Photo/video evidence
- The relevant audit trail entries, showing the hash chain is unbroken

Build one endpoint for this early: `GET /incidents/{id}/evidence-pack` → generates a PDF.
This is the actual client-facing deliverable — not raw access to the audit table.

## 5. Legal framework — South Africa

- **ECT Act (Electronic Communications and Transactions Act)** governs whether an electronic
  record is admissible as evidence — this is the relevant law here, not POPIA.
- POPIA governs *how personal data is handled*; the ECT Act governs *whether the record itself
  can be trusted and used as evidence*.
- Broadly, admissibility turns on whether the system that created/stored the record is
  **reliable**, and whether the **integrity** of the data has been maintained since creation.
  That maps directly onto points 1–3 above: hash-chained log, locked-down DB permissions,
  server-side timestamps, and knowing which authenticated user/device performed each action
  (non-repudiation).
- Not a legal opinion — if a client needs formal admissibility sign-off, that should go to an
  actual lawyer. The engineering goal is to build toward that standard, not to certify it.

---

## What to build now vs. later

**Do immediately (cheap, foundational):**
- Hash-chain the `audit_log` table
- Lock DB permissions so the app can only `INSERT` into `audit_log`
- Server-side timestamps everywhere (no client-supplied times trusted)

**Wait until a client asks for it (real demand signal):**
- Evidence-pack PDF export
- Off-system backup / write-once export job

Building the second group before a client asks for it is effort spent on a guess. Building the
first group late means retrofitting integrity onto data that's already been running without it —
better to have it from day one.
