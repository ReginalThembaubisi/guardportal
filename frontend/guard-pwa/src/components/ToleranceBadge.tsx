import Seal from "./Seal";

/**
 * Shared read-out for the flag-don't-block GPS check used by both shift
 * clock-in/out and checkpoint scans. null means the property/checkpoint had
 * no known location to compare against yet — not a pass, just unverifiable,
 * rendered as its own distinct seal state rather than folded into "flagged".
 */
export default function ToleranceBadge({
  withinTolerance,
  distanceMeters,
}: {
  withinTolerance: boolean | null;
  distanceMeters: number | null;
}) {
  if (withinTolerance === null) {
    return <Seal state="unverified">Unverified — no reference point set</Seal>;
  }
  if (withinTolerance) {
    return <Seal state="cleared">In range{distanceMeters !== null && ` · ${distanceMeters}m`}</Seal>;
  }
  return <Seal state="flagged">Flagged · {distanceMeters}m from expected location</Seal>;
}
