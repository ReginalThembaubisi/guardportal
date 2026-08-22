import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { MissedCheckpointResponse, PatrolRouteResponse, PropertyManagerResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in local time — no timezone suffix. */
function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function PatrolStatusPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyManagerResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [routes, setRoutes] = useState<PatrolRouteResponse[] | null>(null);
  const [selectedRouteId, setSelectedRouteId] = useState<number | null>(null);

  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const [from, setFrom] = useState(toLocalInputValue(startOfToday));
  const [to, setTo] = useState(toLocalInputValue(new Date()));

  const [status, setStatus] = useState<MissedCheckpointResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!auth) return;
    apiFetch<PropertyManagerResponse[]>("/api/v1/property-managers/mine", { token: auth.token })
      .then((props) => {
        setProperties(props);
        if (props.length > 0) setSelectedPropertyId(props[0].propertyId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your properties"));
  }, [auth]);

  useEffect(() => {
    if (!auth || selectedPropertyId === null) return;
    setRoutes(null);
    setSelectedRouteId(null);
    setStatus(null);
    apiFetch<PatrolRouteResponse[]>(`/api/v1/patrol-routes?propertyId=${selectedPropertyId}`, { token: auth.token })
      .then((r) => {
        setRoutes(r);
        if (r.length > 0) setSelectedRouteId(r[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load patrol routes"));
  }, [auth, selectedPropertyId]);

  async function checkStatus(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedRouteId === null) return;
    setError(null);
    setBusy(true);
    try {
      const result = await apiFetch<MissedCheckpointResponse>(
        `/api/v1/patrol-routes/${selectedRouteId}/checkpoint-status?from=${from}&to=${to}`,
        { token: auth.token },
      );
      setStatus(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load patrol status");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Patrol Status">
      {error && <p className="error">{error}</p>}

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {routes && routes.length === 0 && (
        <p className="empty">No patrol routes on this property yet — add one first.</p>
      )}

      {properties && properties.length > 0 && routes && routes.length > 0 && (
        <form onSubmit={checkStatus}>
          {properties.length > 1 && (
            <label>
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

          <label>
            Route
            <select value={selectedRouteId ?? ""} onChange={(e) => setSelectedRouteId(Number(e.target.value))}>
              {routes.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            From
            <input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} required />
          </label>
          <label>
            To
            <input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} required />
          </label>

          <button type="submit" disabled={busy}>
            {busy ? "Checking…" : "Check status"}
          </button>
        </form>
      )}

      {status && (
        <>
          <h2 style={{ marginTop: 24 }}>
            {status.routeName} — {new Date(status.from).toLocaleString()} to {new Date(status.to).toLocaleString()}
          </h2>
          <table className="entries-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Checkpoint</th>
                <th>Status</th>
                <th>Scans</th>
                <th>First scan</th>
                <th>Last scan</th>
              </tr>
            </thead>
            <tbody>
              {status.checkpoints.map((c) => (
                <tr key={c.checkpointId}>
                  <td>{c.sequenceOrder}</td>
                  <td>{c.name}</td>
                  <td>
                    {c.scanned ? (
                      <span className="badge recognized">scanned</span>
                    ) : (
                      <span className="badge missed">missed</span>
                    )}
                  </td>
                  <td>{c.scanCount}</td>
                  <td>{c.firstScanAt ? new Date(c.firstScanAt).toLocaleString() : "—"}</td>
                  <td>{c.lastScanAt ? new Date(c.lastScanAt).toLocaleString() : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </Layout>
  );
}
