import { useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { VisitorEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

/** No Layout wrapper — reused both as its own route and inside Gate's Vehicles segment. */
export function VehicleHistoryContent() {
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
    <>
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
        <section className="occupancy-category" style={{ marginTop: 20 }}>
          <h2>
            Visits <span className="count-badge">{entries.length}</span>
          </h2>
          <ul>
            {entries.map((entry) => (
              <li key={entry.id}>
                <strong>{entry.visitorName}</strong>
                <span className="entry-meta">
                  {entry.category}
                  {" · entered "}
                  {new Date(entry.enteredAt).toLocaleString()}
                  {entry.exitedAt ? ` · exited ${new Date(entry.exitedAt).toLocaleString()}` : " · on site"}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

export default function VehicleHistoryPage() {
  return (
    <Layout title="Vehicle History">
      <VehicleHistoryContent />
    </Layout>
  );
}
