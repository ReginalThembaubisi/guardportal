import type { ReactNode } from "react";

export type SealState = "pending" | "cleared" | "flagged" | "unverified" | "denied";

/**
 * The one verification mark every surface shares. Five states, each mapped
 * to a real value already in the backend — never invented:
 *   pending    — request in flight, not yet confirmed by the server
 *   cleared    — confirmed / within tolerance / approved
 *   flagged    — confirmed, but outside tolerance (never blocks)
 *   unverified — no reference point exists to check against (tolerance is
 *                null, not false) — kept distinct from flagged on purpose
 *   denied     — visitor_entry.approval_status === DENIED
 */
export default function Seal({ state, children }: { state: SealState; children: ReactNode }) {
  return <span className={`seal ${state}`}>{children}</span>;
}
