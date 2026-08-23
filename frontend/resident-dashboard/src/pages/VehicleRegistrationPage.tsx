import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { VehicleResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function VehicleRegistrationPage() {
  const { auth } = useAuth();
  const [vehicles, setVehicles] = useState<VehicleResponse[] | null>(null);
  const [registration, setRegistration] = useState("");
  const [make, setMake] = useState("");
  const [model, setModel] = useState("");
  const [colour, setColour] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function loadVehicles() {
    if (!auth) return;
    apiFetch<VehicleResponse[]>("/api/v1/vehicles", { token: auth.token })
      .then(setVehicles)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your vehicles"));
  }

  useEffect(loadVehicles, [auth]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      await apiFetch("/api/v1/vehicles", {
        method: "POST",
        token: auth.token,
        body: {
          registration: registration.trim().toUpperCase(),
          make: make.trim() || undefined,
          model: model.trim() || undefined,
          colour: colour.trim() || undefined,
        },
      });
      setRegistration("");
      setMake("");
      setModel("");
      setColour("");
      loadVehicles();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to register vehicle");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="My Vehicles">
      {error && <p className="error">{error}</p>}

      <form onSubmit={submit}>
        <label>
          Registration
          <input
            type="text"
            value={registration}
            onChange={(e) => setRegistration(e.target.value.toUpperCase())}
            placeholder="e.g. CA123456"
            required
          />
        </label>
        <label>
          Make (optional)
          <input type="text" value={make} onChange={(e) => setMake(e.target.value)} placeholder="e.g. Toyota" />
        </label>
        <label>
          Model (optional)
          <input type="text" value={model} onChange={(e) => setModel(e.target.value)} placeholder="e.g. Corolla" />
        </label>
        <label>
          Colour (optional)
          <input type="text" value={colour} onChange={(e) => setColour(e.target.value)} placeholder="e.g. White" />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Registering…" : "Register vehicle"}
        </button>
      </form>

      <h2 style={{ marginTop: 24 }}>Registered vehicles</h2>
      {vehicles && vehicles.length === 0 && <p className="empty">No vehicles registered yet.</p>}
      {vehicles && vehicles.length > 0 && (
        <table className="entries-table">
          <thead>
            <tr>
              <th>Registration</th>
              <th>Make</th>
              <th>Model</th>
              <th>Colour</th>
            </tr>
          </thead>
          <tbody>
            {vehicles.map((v) => (
              <tr key={v.id}>
                <td>{v.registration}</td>
                <td>{v.make ?? "—"}</td>
                <td>{v.model ?? "—"}</td>
                <td>{v.colour ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
