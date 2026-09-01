import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { PropertyManagerResponse, UnitResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function UnitsPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyManagerResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [units, setUnits] = useState<UnitResponse[] | null>(null);
  const [unitNumber, setUnitNumber] = useState("");
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

  function loadUnits() {
    if (!auth || selectedPropertyId === null) return;
    apiFetch<UnitResponse[]>(`/api/v1/properties/${selectedPropertyId}/units`, { token: auth.token })
      .then(setUnits)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load units"));
  }

  useEffect(loadUnits, [auth, selectedPropertyId]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedPropertyId === null) return;
    setError(null);
    setBusy(true);
    try {
      await apiFetch(`/api/v1/properties/${selectedPropertyId}/units`, {
        method: "POST",
        token: auth.token,
        body: { unitNumber: unitNumber.trim() },
      });
      setUnitNumber("");
      loadUnits();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create unit");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Units">
      {error && <p className="error">{error}</p>}

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {properties && properties.length > 0 && (
        <>
          {properties.length > 1 && (
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

          <form onSubmit={submit}>
            <label>
              Unit number
              <input
                type="text"
                value={unitNumber}
                onChange={(e) => setUnitNumber(e.target.value)}
                placeholder="e.g. 42"
                required
              />
            </label>
            <button type="submit" disabled={busy}>
              {busy ? "Creating…" : "Create unit"}
            </button>
          </form>

          <h2 style={{ marginTop: 24 }}>Units</h2>
          {units && units.length === 0 && <p className="empty">No units on this property yet.</p>}
          {units && units.length > 0 && (
            <table className="entries-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Unit number</th>
                </tr>
              </thead>
              <tbody>
                {units.map((u) => (
                  <tr key={u.id}>
                    <td>{u.id}</td>
                    <td>{u.unitNumber}</td>
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
