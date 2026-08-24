import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { PropertyClientResponse, ResidentResponse, UnitResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function ClientResidentsPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyClientResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [units, setUnits] = useState<UnitResponse[] | null>(null);
  const [residents, setResidents] = useState<ResidentResponse[] | null>(null);

  const [selectedUnitId, setSelectedUnitId] = useState<number | null>(null);
  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [removingId, setRemovingId] = useState<number | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<PropertyClientResponse[]>("/api/v1/property-clients/mine", { token: auth.token })
      .then((props) => {
        setProperties(props);
        if (props.length > 0) setSelectedPropertyId(props[0].propertyId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your properties"));
  }, [auth]);

  useEffect(() => {
    if (!auth || selectedPropertyId === null) return;
    apiFetch<UnitResponse[]>(`/api/v1/properties/${selectedPropertyId}/units`, { token: auth.token })
      .then((u) => {
        setUnits(u);
        setSelectedUnitId(u.length > 0 ? u[0].id : null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load units"));
  }, [auth, selectedPropertyId]);

  function loadResidents() {
    if (!auth) return;
    apiFetch<ResidentResponse[]>("/api/v1/residents", { token: auth.token })
      .then(setResidents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load residents"));
  }

  useEffect(loadResidents, [auth]);

  // The API returns every resident across all properties this client owns —
  // narrow to whichever property is currently selected using the unit ids
  // already scoped to it (ResidentResponse doesn't carry a propertyId itself).
  const unitIdsForSelectedProperty = new Set((units ?? []).map((u) => u.id));
  const residentsForSelectedProperty = (residents ?? []).filter((r) => unitIdsForSelectedProperty.has(r.unitId));

  async function submitAdd(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedUnitId === null) return;
    setError(null);
    setBusy(true);
    try {
      await apiFetch<ResidentResponse>("/api/v1/residents", {
        method: "POST",
        token: auth.token,
        body: {
          unitId: selectedUnitId,
          fullName: fullName.trim(),
          phoneNumber: phoneNumber.trim(),
          email: email.trim() || undefined,
        },
      });
      setFullName("");
      setPhoneNumber("");
      setEmail("");
      loadResidents();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to add resident");
    } finally {
      setBusy(false);
    }
  }

  async function removeResident(id: number) {
    if (!auth) return;
    setError(null);
    setRemovingId(id);
    try {
      await apiFetch(`/api/v1/residents/${id}`, { method: "DELETE", token: auth.token });
      setResidents((prev) => (prev ? prev.filter((r) => r.id !== id) : prev));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to remove resident");
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <Layout title="Residents">
      {error && <p className="error">{error}</p>}

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {properties && properties.length > 1 && (
        <label>
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

      {properties && properties.length > 0 && (
        <>
          <h2>Current residents</h2>
          {residentsForSelectedProperty.length === 0 ? (
            <p className="empty">No residents on file for this property yet.</p>
          ) : (
            <table className="entries-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Unit</th>
                  <th>Phone</th>
                  <th>Email</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {residentsForSelectedProperty.map((r) => (
                  <tr key={r.id}>
                    <td>{r.fullName}</td>
                    <td>{r.unitNumber}</td>
                    <td>{r.phoneNumber}</td>
                    <td>{r.email ?? "—"}</td>
                    <td>
                      <button
                        type="button"
                        className="link-button"
                        onClick={() => removeResident(r.id)}
                        disabled={removingId === r.id}
                      >
                        {removingId === r.id ? "Removing…" : "Remove"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <h2 style={{ marginTop: 24 }}>Add a resident</h2>
          <form onSubmit={submitAdd}>
            <label>
              Unit
              {units && units.length === 0 ? (
                <p className="empty">No units on this property yet. Ask an admin to add one.</p>
              ) : (
                <select value={selectedUnitId ?? ""} onChange={(e) => setSelectedUnitId(Number(e.target.value))} required>
                  {units?.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.unitNumber}
                    </option>
                  ))}
                </select>
              )}
            </label>
            <label>
              Full name
              <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} required />
            </label>
            <label>
              Phone number
              <input
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="+27821234567"
                required
              />
            </label>
            <label>
              Email (optional)
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <button type="submit" disabled={busy || !units || units.length === 0}>
              {busy ? "Adding…" : "Add resident"}
            </button>
          </form>
        </>
      )}
    </Layout>
  );
}
