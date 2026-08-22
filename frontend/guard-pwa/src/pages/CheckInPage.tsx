import { useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { VisitorEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import QrScanner from "../components/QrScanner";

export default function CheckInPage() {
  const { auth, setPropertyId } = useAuth();
  const [qrToken, setQrToken] = useState("");
  const [vehicleRegistration, setVehicleRegistration] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [lastCheckIn, setLastCheckIn] = useState<VisitorEntryResponse | null>(null);
  const [busy, setBusy] = useState(false);

  async function submitCheckIn(token: string) {
    if (!auth || busy) return;
    setError(null);
    setBusy(true);
    try {
      const entry = await apiFetch<VisitorEntryResponse>("/api/v1/visitor-entries", {
        method: "POST",
        token: auth.token,
        body: {
          qrToken: token.trim(),
          vehicleRegistration: vehicleRegistration.trim() || undefined,
        },
      });
      setLastCheckIn(entry);
      setPropertyId(entry.propertyId);
      setQrToken("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Check-in failed");
    } finally {
      setBusy(false);
    }
  }

  function handleManualSubmit(e: FormEvent) {
    e.preventDefault();
    submitCheckIn(qrToken);
  }

  return (
    <Layout title="Check In">
      {error && <p className="error">{error}</p>}

      <label>
        Vehicle registration (optional)
        <input
          type="text"
          value={vehicleRegistration}
          onChange={(e) => setVehicleRegistration(e.target.value.toUpperCase())}
          placeholder="e.g. CA123456 — fill in before scanning if there's a vehicle"
        />
      </label>

      <QrScanner onDecode={submitCheckIn} />

      <p className="scanner-divider">or enter the code manually</p>

      <form onSubmit={handleManualSubmit}>
        <label>
          Check-in code
          <input
            type="text"
            value={qrToken}
            onChange={(e) => setQrToken(e.target.value)}
            placeholder="Type the visitor's code"
            required
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
