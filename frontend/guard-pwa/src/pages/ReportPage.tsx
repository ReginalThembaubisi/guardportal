import { useState, type ChangeEvent, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { IncidentResponse, IncidentSeverity } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import { getCurrentCoordinates } from "../geo";

const SEVERITIES: IncidentSeverity[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

const SEVERITY_COLOR: Record<IncidentSeverity, string> = {
  LOW: "var(--accent)",
  MEDIUM: "var(--flag)",
  HIGH: "var(--danger)",
  CRITICAL: "var(--danger)",
};

const SEVERITY_LABEL: Record<IncidentSeverity, string> = {
  LOW: "Low",
  MEDIUM: "Med",
  HIGH: "High",
  CRITICAL: "Crit",
};

/** Rare, but urgent — a guard reaching for this is already in a bad moment, so it's a permanent tab, never behind a drawer. */
export default function ReportPage() {
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
    <Layout title="Report">
      <div className="screen-content">
        {error && <p className="error">{error}</p>}

        <form onSubmit={submit}>
          <label>
            What happened
            <textarea
              className="guard-input"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe what you saw or what happened"
              rows={4}
              required
            />
          </label>
          <label>
            Severity
            <div className="segmented-control cols-4" role="radiogroup">
              {SEVERITIES.map((s) => (
                <button
                  key={s}
                  type="button"
                  className={severity === s ? "active" : ""}
                  style={severity === s ? { background: SEVERITY_COLOR[s] } : undefined}
                  onClick={() => setSeverity(s)}
                >
                  {SEVERITY_LABEL[s]}
                </button>
              ))}
            </div>
          </label>
          <label>
            Photos (optional)
            <input type="file" accept="image/*" multiple capture="environment" onChange={handlePhotoChange} />
          </label>
          {photos.length > 0 && <p className="dev-hint">{photos.length} photo(s) selected</p>}
          <button type="submit" className="report-submit" disabled={busy}>
            {busy ? "Reporting…" : "Report incident"}
          </button>
        </form>

        {reported && (
          <div
            className="checkin-result"
            style={
              reported.severity === "LOW"
                ? undefined
                : reported.severity === "MEDIUM"
                  ? { background: "var(--flag-tint)", borderColor: "var(--flag)" }
                  : { background: "var(--danger-tint)", borderColor: "var(--danger)" }
            }
          >
            <h2 style={reported.severity === "MEDIUM" ? { color: "var(--flag)" } : reported.severity !== "LOW" ? { color: "var(--danger)" } : undefined}>
              Incident reported
            </h2>
            <p className="checkin-visitor-name">{reported.severity}</p>
            <p className="entry-meta">
              {reported.description}
              {" · "}
              {new Date(reported.reportedAt).toLocaleTimeString()}
              {reported.media.length > 0 && ` · ${reported.media.length} photo(s) attached`}
            </p>
          </div>
        )}
      </div>
    </Layout>
  );
}
