import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { CheckpointResponse, PatrolRouteResponse, PropertyManagerResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function CreatePatrolRoutePage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyManagerResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [checkpoints, setCheckpoints] = useState<CheckpointResponse[] | null>(null);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);

  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<PatrolRouteResponse | null>(null);
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
    setCheckpoints(null);
    setSelectedIds([]);
    apiFetch<CheckpointResponse[]>(`/api/v1/checkpoints?propertyId=${selectedPropertyId}`, { token: auth.token })
      .then(setCheckpoints)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load checkpoints"));
  }, [auth, selectedPropertyId]);

  function addCheckpoint(id: number) {
    setSelectedIds((prev) => (prev.includes(id) ? prev : [...prev, id]));
  }

  function removeCheckpoint(id: number) {
    setSelectedIds((prev) => prev.filter((cid) => cid !== id));
  }

  function move(id: number, direction: -1 | 1) {
    setSelectedIds((prev) => {
      const index = prev.indexOf(id);
      const target = index + direction;
      if (target < 0 || target >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedPropertyId === null || selectedIds.length === 0) return;
    setError(null);
    setBusy(true);
    try {
      const route = await apiFetch<PatrolRouteResponse>("/api/v1/patrol-routes", {
        method: "POST",
        token: auth.token,
        body: { propertyId: selectedPropertyId, name: name.trim(), checkpointIds: selectedIds },
      });
      setCreated(route);
      setName("");
      setSelectedIds([]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create patrol route");
    } finally {
      setBusy(false);
    }
  }

  const checkpointName = (id: number) => checkpoints?.find((c) => c.id === id)?.name ?? `#${id}`;

  return (
    <Layout title="Add Patrol Route">
      {error && <p className="error">{error}</p>}

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {properties && properties.length > 0 && (
        <form onSubmit={submit}>
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
            Route name
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Perimeter Patrol" required />
          </label>

          {checkpoints && checkpoints.length === 0 && (
            <p className="empty">No checkpoints on this property yet — add one first.</p>
          )}

          {checkpoints && checkpoints.length > 0 && (
            <div className="route-builder">
              <div className="route-builder-column">
                <h2>Available checkpoints</h2>
                <ul className="route-checkpoint-list">
                  {checkpoints
                    .filter((c) => !selectedIds.includes(c.id))
                    .map((c) => (
                      <li key={c.id}>
                        {c.name}
                        <button type="button" className="refresh-button" onClick={() => addCheckpoint(c.id)}>
                          Add
                        </button>
                      </li>
                    ))}
                </ul>
              </div>

              <div className="route-builder-column">
                <h2>Route order</h2>
                {selectedIds.length === 0 && <p className="empty">No checkpoints added yet.</p>}
                <ul className="route-checkpoint-list">
                  {selectedIds.map((id, index) => (
                    <li key={id}>
                      <span className="count-badge">{index + 1}</span> {checkpointName(id)}
                      <span className="route-checkpoint-actions">
                        <button type="button" className="link-button" onClick={() => move(id, -1)} disabled={index === 0}>
                          ↑
                        </button>
                        <button
                          type="button"
                          className="link-button"
                          onClick={() => move(id, 1)}
                          disabled={index === selectedIds.length - 1}
                        >
                          ↓
                        </button>
                        <button type="button" className="link-button" onClick={() => removeCheckpoint(id)}>
                          Remove
                        </button>
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          <button type="submit" disabled={busy || selectedIds.length === 0}>
            {busy ? "Creating…" : "Create route"}
          </button>
        </form>
      )}

      {created && (
        <div className="invitation-result">
          <h2>Route created</h2>
          <p className="checkin-visitor-name">{created.name}</p>
          <ol className="entry-meta">
            {created.checkpoints.map((stop) => (
              <li key={stop.checkpointId}>{stop.name}</li>
            ))}
          </ol>
        </div>
      )}
    </Layout>
  );
}
