import type { ReactNode } from "react";

export type SealState = "pending" | "cleared" | "flagged" | "unverified" | "denied" | "late";

/**
 * The one verification mark every surface shares — reserved for genuine
 * verification (a checkpoint actually scanned, a vehicle actually
 * recognized), never for triage workflow status. See guard-pwa's Seal for
 * the full state-to-backend-field mapping; both components share the exact
 * same five states on purpose.
 */
export default function Seal({ state, children }: { state: SealState; children: ReactNode }) {
  return <span className={`seal ${state}`}>{children}</span>;
}
