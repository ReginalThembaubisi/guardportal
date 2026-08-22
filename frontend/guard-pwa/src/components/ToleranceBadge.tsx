/**
 * Shared read-out for the flag-don't-block GPS check used by both shift
 * clock-in/out and checkpoint scans. null means the property/checkpoint had
 * no known location to compare against yet — not a pass, just unverifiable.
 */
export default function ToleranceBadge({
  withinTolerance,
  distanceMeters,
}: {
  withinTolerance: boolean | null;
  distanceMeters: number | null;
}) {
  if (withinTolerance === null) {
    return <span className="tolerance-badge tolerance-unknown">Location not verified (no reference point set)</span>;
  }
  if (withinTolerance) {
    return (
      <span className="tolerance-badge tolerance-ok">
        ✓ In range{distanceMeters !== null && ` (${distanceMeters}m)`}
      </span>
    );
  }
  return (
    <span className="tolerance-badge tolerance-flag">
      ⚠ Flagged — {distanceMeters}m from expected location
    </span>
  );
}
