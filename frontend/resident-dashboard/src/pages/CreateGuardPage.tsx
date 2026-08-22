import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { GuardResponse, PropertyManagerResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function CreateGuardPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyManagerResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);

  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [badgeNumber, setBadgeNumber] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<GuardResponse | null>(null);
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

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedPropertyId === null) return;
    setError(null);
    setBusy(true);
    try {
      const guard = await apiFetch<GuardResponse>("/api/v1/guards", {
        method: "POST",
        token: auth.token,
        body: {
          propertyId: selectedPropertyId,
          fullName: fullName.trim(),
          phoneNumber: phoneNumber.trim(),
          email: email.trim(),
          password,
          badgeNumber: badgeNumber.trim() || undefined,
        },
      });
      setCreated(guard);
      setFullName("");
      setPhoneNumber("");
      setEmail("");
      setPassword("");
      setBadgeNumber("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create guard");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Add Guard">
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
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <label>
            Initial password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={8}
              placeholder="At least 8 characters"
              required
            />
          </label>
          <label>
            Badge number (optional)
            <input type="text" value={badgeNumber} onChange={(e) => setBadgeNumber(e.target.value)} />
          </label>

          <button type="submit" disabled={busy}>
            {busy ? "Creating…" : "Add guard"}
          </button>
        </form>
      )}

      {created && (
        <div className="invitation-result">
          <h2>Guard added</h2>
          <p className="checkin-visitor-name">{created.fullName}</p>
          <p className="entry-meta">
            {created.propertyName} · {created.email}
            {created.badgeNumber && ` · Badge ${created.badgeNumber}`}
          </p>
          <p className="dev-hint">Share their email and the password you just set — they log in with those in the guard app.</p>
        </div>
      )}
    </Layout>
  );
}
