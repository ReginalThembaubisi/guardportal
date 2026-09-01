import type { ReactNode } from "react";

export type SealState = "pending" | "cleared" | "flagged" | "unverified" | "denied";

/**
 * The one verification mark every surface shares. Five states, each mapped
 * to a real value already in the backend — never invented:
 *   pending    — request in flight, not yet confirmed by the server
 *   cleared    — confirmed / approved
 *   flagged    — confirmed, but outside expected bounds (never blocks).
 *                Patrol-specific: checkpoint scan outside the checkpoint radius.
 *   unverified — no reference point exists to compare against — kept distinct
 *                from flagged on purpose (unknown ≠ failed)
 *   denied     — visitor_entry.approval_status === DENIED
 */
export default function Seal({ state, children }: { state: SealState; children: ReactNode }) {
  return <span className={`seal ${state}`}>{children}</span>;
}
