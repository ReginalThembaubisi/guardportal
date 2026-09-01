# Known Gaps and Deferred Work

## `clock_out_source` — no property-manager visibility (V15)

The `clock_out_source` column on `shift` records whether a guard's clock-out time could be
verified by the server (`null` = normal path) or arrived too late for the server to vouch for
it (`CLIENT_CLAIMED_LATE` = guard submitted via the expired queue entry path).

The column is only worth its cost if a human reads it. The right surface is a shift list or
payroll export in the resident-dashboard property-manager screens — flagged rows could trigger
a follow-up before payroll runs. That screen is out of scope for the current phase.

**Until it is built, the flag sits unread in the database.** This is the unclosed shift problem
one layer down: the anomaly is recorded but invisible to the person who needs to act on it.
Track this alongside any resident-dashboard shift-reporting work in a future phase.
