import { useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { CheckpointScanResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import ToleranceBadge from "../components/ToleranceBadge";
import { getCurrentCoordinates } from "../geo";

export default function CheckpointScanPage() {
  const { auth } = useAuth();
  const [qrToken, setQrToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [lastScan, setLastScan] = useState<CheckpointScanResponse | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      const coords = await getCurrentCoordinates();
      const scan = await apiFetch<CheckpointScanResponse>("/api/v1/checkpoint-scans", {
        method: "POST",
        token: auth.token,
        body: { qrToken: qrToken.trim(), latitude: coords.latitude, longitude: coords.longitude },
      });
      setLastScan(scan);
      setQrToken("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : "Scan failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Scan Checkpoint">
      {!auth?.openShift && (
        <p className="dev-hint">You don't look clocked in — checkpoint scans require an open shift.</p>
      )}
      {error && <p className="error">{error}</p>}

      <form onSubmit={submit}>
        <label>
          Checkpoint code
          <input
            type="text"
            value={qrToken}
            onChange={(e) => setQrToken(e.target.value)}
            placeholder="Paste or type the checkpoint's code"
            autoFocus
            required
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Scanning…" : "Scan"}
        </button>
      </form>

      {lastScan && (
        <div className="checkin-result">
          <h2>Scanned</h2>
          <p className="checkin-visitor-name">{lastScan.checkpointName}</p>
          <p className="entry-meta">{new Date(lastScan.scannedAt).toLocaleTimeString()}</p>
          <ToleranceBadge withinTolerance={lastScan.withinTolerance} distanceMeters={lastScan.distanceMeters} />
        </div>
      )}
    </Layout>
  );
}
