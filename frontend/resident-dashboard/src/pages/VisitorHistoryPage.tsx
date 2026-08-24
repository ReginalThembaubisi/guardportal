import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { PropertyManagerResponse, PropertySupervisorResponse, VisitorHistoryEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

interface PropertyOption {
  propertyId: number;
  propertyName: string;
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function VisitorHistoryPage() {
  const { auth, hasRole } = useAuth();
  const [properties, setProperties] = useState<PropertyOption[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [entries, setEntries] = useState<VisitorHistoryEntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!auth) return;
    const path = hasRole("PROPERTY_MANAGER") ? "/api/v1/property-managers/mine" : "/api/v1/property-supervisors/mine";
    apiFetch<PropertyManagerResponse[] | PropertySupervisorResponse[]>(path, { token: auth.token })
      .then((props) => {
        setProperties(props);
        if (props.length > 0) setSelectedPropertyId(props[0].propertyId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your properties"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth]);

  const search = useCallback(() => {
    if (!auth || selectedPropertyId === null) return;
    setError(null);
    setBusy(true);
    apiFetch<VisitorHistoryEntryResponse[]>(
      `/api/v1/properties/${selectedPropertyId}/visitor-entries/history?from=${from}&to=${to}`,
      { token: auth.token },
    )
      .then(setEntries)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load visitor history"))
      .finally(() => setBusy(false));
  }, [auth, selectedPropertyId, from, to]);

  useEffect(search, [search]);

  return (
    <Layout title="Visitor History">
      {error && <p className="error">{error}</p>}
      <p className="dev-hint">
        Who was checked in on a given day or date range — the digital equivalent of pulling a paper
        register for a specific date. For incident investigation, not routine browsing.
      </p>

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {properties && properties.length > 1 && (
        <label className="property-select">
          Property
          <select value={selectedPropertyId ?? ""} onChange={(e) => setSelectedPropertyId(Number(e.target.value))}>
            {properties.map((p) => (
              <option key={p.propertyId} value={p.propertyId}>
                {p.propertyName}
              </option>
            ))}
          </select>
        </label>
      )}

      {properties && properties.length > 0 && (
        <>
          <div className="date-range-picker">
            <label>
              From
              <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} max={to} />
            </label>
            <label>
              To
              <input type="date" value={to} onChange={(e) => setTo(e.target.value)} min={from} max={today()} />
            </label>
          </div>

          {busy && <p className="dev-hint">Searching…</p>}

          {!busy && entries && entries.length === 0 && (
            <p className="empty">No visitor entries in this date range.</p>
          )}

          {!busy && entries && entries.length > 0 && (
            <table className="entries-table">
              <thead>
                <tr>
                  <th>Visitor</th>
                  <th>Unit</th>
                  <th>Category</th>
                  <th>Status</th>
                  <th>Vehicle</th>
                  <th>Entered</th>
                  <th>Exited</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((e) => (
                  <tr key={e.id}>
                    <td>{e.visitorName}</td>
                    <td>{e.unitNumber ?? "—"}</td>
                    <td>{e.category}</td>
                    <td>
                      <span className={`badge status-${e.approvalStatus.toLowerCase().replace(/_/g, "-")}`}>
                        {e.approvalStatus}
                      </span>
                    </td>
                    <td>{e.vehicleRegistration ?? "—"}</td>
                    <td>{new Date(e.enteredAt).toLocaleString()}</td>
                    <td>{e.exitedAt ? new Date(e.exitedAt).toLocaleString() : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </Layout>
  );
}
