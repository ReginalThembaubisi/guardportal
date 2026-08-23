import { useState, type ChangeEvent, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { IncidentResponse, IncidentSeverity } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import { getCurrentCoordinates } from "../geo";

const SEVERITIES: IncidentSeverity[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export default function ReportIncidentPage() {
  const { auth } = useAuth();
  const [description, setDescription] = useState("");
  const [severity, setSeverity] = useState<IncidentSeverity>("MEDIUM");
  const [photos, setPhotos] = useState<File[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [reported, setReported] = useState<IncidentResponse | null>(null);
  const [busy, setBusy] = useState(false);

  function handlePhotoChange(e: ChangeEvent<HTMLInputElement>) {
    setPhotos(e.target.files ? Array.from(e.target.files) : []);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      const coords = await getCurrentCoordinates();

      const formData = new FormData();
      formData.append("description", description.trim());
      formData.append("severity", severity);
      formData.append("latitude", String(coords.latitude));
      formData.append("longitude", String(coords.longitude));
      photos.forEach((photo) => formData.append("photos", photo));

      const incident = await apiFetch<IncidentResponse>("/api/v1/incidents", {
        method: "POST",
        token: auth.token,
        body: formData,
      });
      setReported(incident);
      setDescription("");
      setSeverity("MEDIUM");
      setPhotos([]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : "Failed to report incident");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Report Incident">
      {error && <p className="error">{error}</p>}

      <form onSubmit={submit}>
        <label>
          What happened
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe what you saw or what happened"
            rows={4}
            required
          />
        </label>
        <label>
          Severity
          <select value={severity} onChange={(e) => setSeverity(e.target.value as IncidentSeverity)}>
            {SEVERITIES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label>
          Photos (optional)
          <input type="file" accept="image/*" multiple capture="environment" onChange={handlePhotoChange} />
        </label>
        {photos.length > 0 && <p className="dev-hint">{photos.length} photo(s) selected</p>}
        <button type="submit" disabled={busy}>
          {busy ? "Reporting…" : "Report incident"}
        </button>
      </form>

      {reported && (
        <div className="checkin-result">
          <h2>Incident reported</h2>
          <p className="checkin-visitor-name">{reported.severity}</p>
          <p className="entry-meta">
            {reported.description}
            {" · "}
            {new Date(reported.reportedAt).toLocaleTimeString()}
            {reported.media.length > 0 && ` · ${reported.media.length} photo(s) attached`}
          </p>
        </div>
      )}
    </Layout>
  );
}
