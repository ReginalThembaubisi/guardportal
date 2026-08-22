import { useCallback, useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { OccupancyResponse, VisitorCategory } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

const CATEGORIES: VisitorCategory[] = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF"];

export default function OccupancyPage() {
  const { auth, setPropertyId } = useAuth();
  const [propertyIdInput, setPropertyIdInput] = useState("");
  const [occupancy, setOccupancy] = useState<OccupancyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exitingId, setExitingId] = useState<number | null>(null);

  const loadOccupancy = useCallback(() => {
    if (!auth || auth.propertyId === null) return;
    setError(null);
    apiFetch<OccupancyResponse>(`/api/v1/properties/${auth.propertyId}/occupancy`, { token: auth.token })
      .then(setOccupancy)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load occupancy"));
  }, [auth]);

  useEffect(() => {
    loadOccupancy();
  }, [loadOccupancy]);

  function submitPropertyId(e: FormEvent) {
    e.preventDefault();
    const id = Number(propertyIdInput);
    if (Number.isFinite(id) && id > 0) {
      setPropertyId(id);
    }
  }

  async function handleExit(entryId: number) {
    if (!auth) return;
    setError(null);
    setExitingId(entryId);
    try {
      await apiFetch(`/api/v1/visitor-entries/${entryId}/exit`, { method: "POST", token: auth.token });
      loadOccupancy();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to check visitor out");
    } finally {
      setExitingId(null);
    }
  }

  if (!auth || auth.propertyId === null) {
    return (
      <Layout title="Occupancy">
        <p className="empty">
          Your property hasn't been detected yet — it's picked up automatically after your first check-in. You can
          also enter it directly if you know it:
        </p>
        <form onSubmit={submitPropertyId}>
          <label>
            Property ID
            <input
              type="number"
              min={1}
              value={propertyIdInput}
              onChange={(e) => setPropertyIdInput(e.target.value)}
              required
            />
          </label>
          <button type="submit">Set property</button>
        </form>
      </Layout>
    );
  }

  return (
    <Layout title="Occupancy">
      {error && <p className="error">{error}</p>}

      {occupancy && (
        <>
          <div className="occupancy-summary">
            <span className="total-count">{occupancy.totalOnSite}</span> on site right now
            <button className="refresh-button" onClick={loadOccupancy}>
              Refresh
            </button>
          </div>

          <div className="occupancy-grid">
            {CATEGORIES.map((category) => {
              const entries = occupancy.byCategory[category] ?? [];
              return (
                <section key={category} className="occupancy-category">
                  <h2>
                    {category} <span className="count-badge">{entries.length}</span>
                  </h2>
                  {entries.length === 0 ? (
                    <p className="empty">None</p>
                  ) : (
                    <ul>
                      {entries.map((entry) => (
                        <li key={entry.id}>
                          <div className="entry-row">
                            <div>
                              <strong>{entry.visitorName}</strong>
                              <span className="entry-meta">
                                {entry.vehicleRegistration && (
                                  <>
                                    {" "}
                                    · {entry.vehicleRegistration}
                                    {entry.vehicleRecognized && <span className="badge recognized"> recognized</span>}
                                  </>
                                )}
                                {" · entered "}
                                {new Date(entry.enteredAt).toLocaleTimeString()}
                              </span>
                            </div>
                            <button
                              className="exit-button"
                              onClick={() => handleExit(entry.id)}
                              disabled={exitingId === entry.id}
                            >
                              {exitingId === entry.id ? "Checking out…" : "Check out"}
                            </button>
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              );
            })}
          </div>
        </>
      )}
    </Layout>
  );
}
