import { useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { VisitorEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function VehicleHistoryPage() {
  const { auth } = useAuth();
  const [registration, setRegistration] = useState("");
  const [entries, setEntries] = useState<VisitorEntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function search(e: FormEvent) {
    e.preventDefault();
    if (!auth || !registration.trim()) return;
    setError(null);
    setBusy(true);
    try {
      const result = await apiFetch<VisitorEntryResponse[]>(
        `/api/v1/visitor-entries/by-vehicle/${encodeURIComponent(registration.trim().toUpperCase())}`,
        { token: auth.token },
      );
      setEntries(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to look up vehicle history");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Vehicle History">
      {error && <p className="error">{error}</p>}

      <form onSubmit={search}>
        <label>
          Registration
          <input
            type="text"
            value={registration}
            onChange={(e) => setRegistration(e.target.value.toUpperCase())}
            placeholder="e.g. CA123456"
            autoFocus
            required
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Searching…" : "Search"}
        </button>
      </form>

      {entries && entries.length === 0 && <p className="empty">No visits found for this registration.</p>}

      {entries && entries.length > 0 && (
        <table className="entries-table" style={{ marginTop: 20 }}>
          <thead>
            <tr>
              <th>Visitor</th>
              <th>Category</th>
              <th>Entered</th>
              <th>Exited</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.id}>
                <td>
                  {entry.visitorName}
                  {entry.vehicleRecognized && <span className="badge recognized"> recognized</span>}
                </td>
                <td>{entry.category}</td>
                <td>{new Date(entry.enteredAt).toLocaleString()}</td>
                <td>{entry.exitedAt ? new Date(entry.exitedAt).toLocaleString() : <span className="badge on-site">on site</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
