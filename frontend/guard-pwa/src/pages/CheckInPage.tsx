import { useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { VisitorEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function CheckInPage() {
  const { auth, setPropertyId } = useAuth();
  const [qrToken, setQrToken] = useState("");
  const [vehicleRegistration, setVehicleRegistration] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [lastCheckIn, setLastCheckIn] = useState<VisitorEntryResponse | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      const entry = await apiFetch<VisitorEntryResponse>("/api/v1/visitor-entries", {
        method: "POST",
        token: auth.token,
        body: {
          qrToken: qrToken.trim(),
          vehicleRegistration: vehicleRegistration.trim() || undefined,
        },
      });
      setLastCheckIn(entry);
      setPropertyId(entry.propertyId);
      setQrToken("");
      setVehicleRegistration("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Check-in failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Check In">
      {error && <p className="error">{error}</p>}

      <form onSubmit={submit}>
        <label>
          Check-in code
          <input
            type="text"
            value={qrToken}
            onChange={(e) => setQrToken(e.target.value)}
            placeholder="Paste or type the visitor's code"
            autoFocus
            required
          />
        </label>
        <label>
          Vehicle registration (optional)
          <input
            type="text"
            value={vehicleRegistration}
            onChange={(e) => setVehicleRegistration(e.target.value.toUpperCase())}
            placeholder="e.g. CA123456"
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Checking in…" : "Check in"}
        </button>
      </form>

      {lastCheckIn && (
        <div className="checkin-result">
          <h2>Checked in</h2>
          <p className="checkin-visitor-name">{lastCheckIn.visitorName}</p>
          <p className="entry-meta">
            {lastCheckIn.category}
            {lastCheckIn.vehicleRegistration && (
              <>
                {" "}
                · {lastCheckIn.vehicleRegistration}
                {lastCheckIn.vehicleRecognized && <span className="badge recognized"> recognized</span>}
              </>
            )}
            {" · "}
            {new Date(lastCheckIn.enteredAt).toLocaleTimeString()}
          </p>
        </div>
      )}
    </Layout>
  );
}
