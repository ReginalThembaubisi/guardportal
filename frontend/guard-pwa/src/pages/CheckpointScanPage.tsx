import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { CheckpointResponse, CheckpointScanResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import ToleranceBadge from "../components/ToleranceBadge";
import { getCurrentCoordinates } from "../geo";

export default function CheckpointScanPage() {
  const { auth } = useAuth();
  const [checkpoints, setCheckpoints] = useState<CheckpointResponse[] | null>(null);
  const [selectedCheckpointId, setSelectedCheckpointId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lastScan, setLastScan] = useState<CheckpointScanResponse | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!auth || auth.propertyId === null) return;
    apiFetch<CheckpointResponse[]>(`/api/v1/checkpoints?propertyId=${auth.propertyId}`, { token: auth.token })
      .then((list) => {
        setCheckpoints(list);
        setSelectedCheckpointId((prev) => (prev !== null && list.some((c) => c.id === prev) ? prev : list[0]?.id ?? null));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load checkpoints"));
  }, [auth]);

  async function submitCheckIn(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedCheckpointId === null || busy) return;
    setError(null);
    setBusy(true);
    try {
      const coords = await getCurrentCoordinates();
      const scan = await apiFetch<CheckpointScanResponse>("/api/v1/checkpoint-scans", {
        method: "POST",
        token: auth.token,
        body: { checkpointId: selectedCheckpointId, latitude: coords.latitude, longitude: coords.longitude },
      });
      setLastScan(scan);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : "Check-in failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Checkpoint Check-in">
      {!auth?.openShift && (
        <p className="dev-hint">You don't look clocked in — checkpoint check-ins require an open shift.</p>
      )}
      {error && <p className="error">{error}</p>}

      {checkpoints && checkpoints.length === 0 && (
        <p className="empty">No checkpoints have been set up for your property yet.</p>
      )}

      {checkpoints && checkpoints.length > 0 && (
        <form onSubmit={submitCheckIn}>
          <label>
            Checkpoint
            <select value={selectedCheckpointId ?? ""} onChange={(e) => setSelectedCheckpointId(Number(e.target.value))} required>
              {checkpoints.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={busy}>
            {busy ? "Getting your location…" : "Check in"}
          </button>
        </form>
      )}

      {lastScan && (
        <div className="checkin-result">
          <h2>Checked in</h2>
          <p className="checkin-visitor-name">{lastScan.checkpointName}</p>
          <p className="entry-meta">{new Date(lastScan.scannedAt).toLocaleTimeString()}</p>
          <ToleranceBadge withinTolerance={lastScan.withinTolerance} distanceMeters={lastScan.distanceMeters} />
        </div>
      )}
    </Layout>
  );
}
