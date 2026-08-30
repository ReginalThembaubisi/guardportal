import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { CheckpointResponse, PropertyManagerResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import LocationMapPicker from "../components/LocationMapPicker";

export default function CreateCheckpointPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyManagerResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);

  const [name, setName] = useState("");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [geoToleranceMeters, setGeoToleranceMeters] = useState("");
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<CheckpointResponse | null>(null);
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

  function useCurrentLocation() {
    if (!("geolocation" in navigator)) {
      setError("This browser doesn't support location.");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLatitude(position.coords.latitude.toFixed(7));
        setLongitude(position.coords.longitude.toFixed(7));
        setLocating(false);
      },
      () => {
        setError("Couldn't get your location — enter coordinates manually.");
        setLocating(false);
      },
    );
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedPropertyId === null) return;
    setError(null);
    setBusy(true);
    try {
      const checkpoint = await apiFetch<CheckpointResponse>("/api/v1/checkpoints", {
        method: "POST",
        token: auth.token,
        body: {
          propertyId: selectedPropertyId,
          name: name.trim(),
          latitude: Number(latitude),
          longitude: Number(longitude),
          geoToleranceMeters: geoToleranceMeters.trim() ? Number(geoToleranceMeters) : undefined,
        },
      });
      setCreated(checkpoint);
      setName("");
      setLatitude("");
      setLongitude("");
      setGeoToleranceMeters("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create checkpoint");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Add Checkpoint">
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
            Checkpoint name
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Front Gate" required />
          </label>

          <button type="button" className="refresh-button" onClick={useCurrentLocation} disabled={locating}>
            {locating ? "Getting location…" : "Use my current location"}
          </button>

          <p className="dev-hint">Not standing at the spot? Click the map to drop a pin instead.</p>
          <LocationMapPicker
            latitude={latitude.trim() ? Number(latitude) : null}
            longitude={longitude.trim() ? Number(longitude) : null}
            onPick={(lat, lng) => {
              setLatitude(lat.toFixed(7));
              setLongitude(lng.toFixed(7));
            }}
          />

          <label>
            Latitude
            <input
              type="number"
              step="any"
              value={latitude}
              onChange={(e) => setLatitude(e.target.value)}
              required
            />
          </label>
          <label>
            Longitude
            <input
              type="number"
              step="any"
              value={longitude}
              onChange={(e) => setLongitude(e.target.value)}
              required
            />
          </label>
          <label>
            GPS tolerance in metres (optional)
            <input
              type="number"
              min={1}
              value={geoToleranceMeters}
              onChange={(e) => setGeoToleranceMeters(e.target.value)}
              placeholder="Falls back to the property/global default"
            />
          </label>

          <button type="submit" disabled={busy}>
            {busy ? "Creating…" : "Create checkpoint"}
          </button>
        </form>
      )}

      {created && (
        <div className="invitation-result">
          <h2>Checkpoint created</h2>
          <p className="checkin-visitor-name">{created.name}</p>
          <p className="entry-meta">Guards can now check in here from the guard app's Checkpoint tab.</p>
        </div>
      )}
    </Layout>
  );
}
