import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { OccupancyResponse, PropertyManagerResponse, VisitorCategory } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

const CATEGORIES: VisitorCategory[] = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF"];

export default function OccupancyDashboardPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyManagerResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [occupancy, setOccupancy] = useState<OccupancyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<PropertyManagerResponse[]>("/api/v1/property-managers/mine", { token: auth.token })
      .then((props) => {
        setProperties(props);
        if (props.length > 0) setSelectedPropertyId(props[0].propertyId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your properties"));
  }, [auth]);

  const loadOccupancy = useCallback(() => {
    if (!auth || selectedPropertyId === null) return;
    apiFetch<OccupancyResponse>(`/api/v1/properties/${selectedPropertyId}/occupancy`, { token: auth.token })
      .then(setOccupancy)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load occupancy"));
  }, [auth, selectedPropertyId]);

  useEffect(() => {
    loadOccupancy();
  }, [loadOccupancy]);

  return (
    <Layout title="Live Occupancy">
      {error && <p className="error">{error}</p>}

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {properties && properties.length > 1 && (
        <label className="property-select">
          Property
          <select
            value={selectedPropertyId ?? ""}
            onChange={(e) => setSelectedPropertyId(Number(e.target.value))}
          >
            {properties.map((p) => (
              <option key={p.propertyId} value={p.propertyId}>
                {p.propertyName}
              </option>
            ))}
          </select>
        </label>
      )}

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
