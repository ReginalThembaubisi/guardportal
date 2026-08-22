import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { PropertyManagerResponse, ResidentResponse, UnitResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function CreateResidentPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyManagerResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [units, setUnits] = useState<UnitResponse[] | null>(null);
  const [selectedUnitId, setSelectedUnitId] = useState<number | null>(null);

  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<ResidentResponse | null>(null);
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
    setUnits(null);
    setSelectedUnitId(null);
    apiFetch<UnitResponse[]>(`/api/v1/properties/${selectedPropertyId}/units`, { token: auth.token })
      .then((u) => {
        setUnits(u);
        if (u.length > 0) setSelectedUnitId(u[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load units"));
  }, [auth, selectedPropertyId]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedUnitId === null) return;
    setError(null);
    setBusy(true);
    try {
      const resident = await apiFetch<ResidentResponse>("/api/v1/residents", {
        method: "POST",
        token: auth.token,
        body: {
          unitId: selectedUnitId,
          fullName: fullName.trim(),
          phoneNumber: phoneNumber.trim(),
          email: email.trim() || undefined,
        },
      });
      setCreated(resident);
      setFullName("");
      setPhoneNumber("");
      setEmail("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create resident");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Add Resident">
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
            {busy ? "Creating…" : "Add resident"}
          </button>
        </form>
      )}

      {created && (
        <div className="invitation-result">
          <h2>Resident added</h2>
          <p className="checkin-visitor-name">{created.fullName}</p>
          <p className="entry-meta">
            Unit {created.unitNumber} · {created.phoneNumber}
          </p>
          <p className="dev-hint">
            They can now log in with this phone number — an OTP code will be shown on screen in dev mode.
          </p>
        </div>
      )}
    </Layout>
  );
}
