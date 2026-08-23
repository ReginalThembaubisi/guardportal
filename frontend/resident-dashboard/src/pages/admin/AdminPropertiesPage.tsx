import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../../api/client";
import type { PropertyResponse } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Layout from "../../components/Layout";

export default function AdminPropertiesPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyResponse[] | null>(null);
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [timezone, setTimezone] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function loadProperties() {
    if (!auth) return;
    apiFetch<PropertyResponse[]>("/api/v1/properties", { token: auth.token })
      .then(setProperties)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load properties"));
  }

  useEffect(loadProperties, [auth]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      await apiFetch("/api/v1/properties", {
        method: "POST",
        token: auth.token,
        body: {
          name: name.trim(),
          address: address.trim() || undefined,
          timezone: timezone.trim() || undefined,
        },
      });
      setName("");
      setAddress("");
      setTimezone("");
      loadProperties();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create property");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Admin — Properties">
      {error && <p className="error">{error}</p>}

      <form onSubmit={submit}>
        <label>
          Name
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Final Estate" required />
        </label>
        <label>
          Address (optional)
          <input type="text" value={address} onChange={(e) => setAddress(e.target.value)} />
        </label>
        <label>
          Timezone (optional)
          <input type="text" value={timezone} onChange={(e) => setTimezone(e.target.value)} placeholder="Africa/Johannesburg" />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Creating…" : "Create property"}
        </button>
      </form>

      <h2 style={{ marginTop: 24 }}>Properties</h2>
      {properties && properties.length === 0 && <p className="empty">No properties yet.</p>}
      {properties && properties.length > 0 && (
        <table className="entries-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Address</th>
              <th>Timezone</th>
            </tr>
          </thead>
          <tbody>
            {properties.map((p) => (
              <tr key={p.id}>
                <td>{p.id}</td>
                <td>{p.name}</td>
                <td>{p.address ?? "—"}</td>
                <td>{p.timezone}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
