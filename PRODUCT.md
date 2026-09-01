# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Six roles, three surfaces:

- **RESIDENT** — phone + OTP login. Creates visitor invitations (QR + WhatsApp share), views their own visitor history and pending invitations, pre-registers their vehicles. Uses resident-dashboard from home, on desktop or mobile browser.
- **GUARD** — email + password login. Stands at a property gate or on patrol, often outdoors, sometimes at night, on their own personal phone (BYOD, no dedicated hardware). Clocks in/out with GPS, scans visitor and checkpoint QR codes (camera-first with manual fallback), checks visitors in/out, reports incidents with photo evidence. Speed and legibility matter more than anything else on this surface — every extra second or misread tap is a real cost at a gate. Uses guard-pwa exclusively.
- **PROPERTY_MANAGER** — staff account, linked to one or more properties. Manages residents, guards, checkpoints, patrol routes, and vehicle/incident history for their properties; views occupancy. Uses resident-dashboard's staff area, from a desk.
- **SUPERVISOR** — staff account, linked to one or more properties (supervisor-property link exists specifically to support incident visibility). Views and triages incidents. Uses resident-dashboard's staff area.
- **CLIENT** — role exists in the system; no dedicated screens built yet (a future aggregate dashboard across a client's properties is on the long-term list, not yet scoped).
- **ADMIN** — back-office operator. Creates properties and units, creates staff accounts, links property managers/supervisors to properties, verifies the audit hash chain. Uses resident-dashboard's admin area.

## Product Purpose

A digital replacement for paper-based visitor/vehicle registers at residential and commercial properties, built incrementally toward a full guard/patrol/incident-management platform. It exists because paper registers can't be verified, can't be searched, and can't survive a legal challenge — this product's reason for being is producing records that can.

## Positioning

Visitor logging itself isn't the differentiator — any app can log a name at a gate. What a competing "generic SaaS dashboard" could not truthfully claim is that every write here is hash-chain audited, server-timestamped (never client-supplied), and traceable to an authenticated actor at the database privilege level (the app's own DB user is INSERT-only on the audit table — even a compromised application can't rewrite history). The product's claim is that its records can actually stand up as evidence, under South Africa's ECT Act, not just look tidy in a dashboard.

## Operating Context

- A guard at a gate or on patrol: outdoors, sometimes at night, moving quickly, one hand often occupied, scanning a resident's or a checkpoint's QR code on their own phone. This is the highest-stakes, least forgiving surface in the product.
- A resident at home: creating an invitation before a visitor arrives, or checking who's been to their unit.
- A property manager or supervisor at a desk: triaging incidents, reviewing occupancy, provisioning guards and checkpoints for their property.
- An admin, rarely, doing back-office setup: onboarding a new property end-to-end (property → units → staff → links) and spot-checking that the audit chain hasn't been tampered with.
- Three separate deployable apps share one backend: guard-pwa (guard-only), resident-dashboard (resident + all staff roles, including the admin area), and the Spring Boot API. There is no single unified shell — the visual identity has to hold across three independent apps, not one router.

## Capabilities and Constraints

Confirmed, built, and running:
- Visitor invitations with QR check-in (camera scan with manual code-entry fallback) and WhatsApp share links
- Walk-in/unexpected visitor logging, held at a PENDING approval state (no live approve/deny workflow yet — passive review only)
- Vehicle capture at check-in with auto-recognition against resident-registered vehicles
- Live occupancy dashboard grouped by category (Visitor / Contractor / Delivery / Staff)
- Guard shift clock-in/out with GPS coordinates captured as evidence — never scored against a radius and never blocked. Guards are dropped at the property and clock in on arrival; the coordinate is the record.
- Patrol checkpoints and routes, GPS-flagged scans, a missed-checkpoint status view
- Incident reporting with photo evidence (severity: Low/Medium/High/Critical; status: Open/Investigating/Resolved)
- A hash-chained, server-timestamped audit log covering every write to the operationally significant tables, with a chain-verification check exposed to admins
- Soft-delete only, everywhere — nothing is ever hard-deleted

Explicitly out of scope so far (do not imply these exist in the UI):
- No live push/SMS notifications of any kind (dev-mode OTP/codes are shown on-screen, not sent)
- No evidence-pack PDF export yet (flagged as a known, deliberately deferred gap)
- No CLIENT-facing screens yet
- Pre-pilot: no real client or property has used this system yet — "take it to a real client" is a stated future milestone, not something already true

## Brand Commitments

None. No real product or company name exists in any project document — this is pre-pilot, whitelabel-stage software. Design a clean, neutral identity; do not invent a company name, logo history, or backstory.

## Evidence on Hand

None real. Every property, guard, resident, and incident currently in the system is development/test seed data created while building the product (test estates, test guards, placeholder GPS coordinates). Future work must not fabricate client logos, testimonials, case studies, or screenshots presented as real usage.

## Product Principles

1. **A record is only as good as its defensibility.** Audit and verification are not a feature bolted onto visitor logging — they're the reason the product exists.
2. **Never trust the client.** Timestamps, locations, and identities are always server-asserted, never taken at face value from a phone or browser.
3. **Flag anomalies, don't block operations.** A guard at a gate can't be stopped by an imperfect GPS reading; irregularities are recorded and surfaced for later review, not used to halt work in the field.
4. **Build only what the current, real need justifies** — though this has been deliberately overridden more than once at the dev's explicit request, each time documented in place rather than silently skipped.
5. **Integrity is enforced where it can't be bypassed.** Soft-delete and audit-chain guarantees are backed by database-level privileges, not just application code discipline.

## Accessibility & Inclusion

No formal compliance target (e.g. WCAG audit) is required at this stage. The binding, product-specific requirement is practical: guard-pwa must be legible and operable by someone standing at a gate, possibly at night, moving quickly — real contrast, real tap-target size, not a generic dashboard's density.
