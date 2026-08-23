import { useCallback, useEffect, useState } from "react";
import { apiFetch, apiFetchBlob, ApiError } from "../api/client";
import type {
  IncidentResponse,
  IncidentStatus,
  PropertyManagerResponse,
  PropertySupervisorResponse,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

const STATUSES: IncidentStatus[] = ["OPEN", "INVESTIGATING", "RESOLVED"];

interface PropertyOption {
  propertyId: number;
  propertyName: string;
}

function IncidentPhoto({ incidentId, mediaId, token }: { incidentId: number; mediaId: number; token: string }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    apiFetchBlob(`/api/v1/incidents/${incidentId}/media/${mediaId}`, token).then((u) => {
      objectUrl = u;
      setUrl(u);
    });
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [incidentId, mediaId, token]);

  if (!url) return <div className="incident-photo-loading" />;
  return <img className="incident-photo" src={url} alt="Incident evidence" />;
}

export default function IncidentsPage() {
  const { auth, hasRole } = useAuth();
  const [properties, setProperties] = useState<PropertyOption[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [incidents, setIncidents] = useState<IncidentResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [updatingId, setUpdatingId] = useState<number | null>(null);

  useEffect(() => {
    if (!auth) return;
    const path = hasRole("PROPERTY_MANAGER") ? "/api/v1/property-managers/mine" : "/api/v1/property-supervisors/mine";
    apiFetch<PropertyManagerResponse[] | PropertySupervisorResponse[]>(path, { token: auth.token })
      .then((props) => {
        setProperties(props);
        if (props.length > 0) setSelectedPropertyId(props[0].propertyId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your properties"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth]);

  const loadIncidents = useCallback(() => {
    if (!auth || selectedPropertyId === null) return;
    apiFetch<IncidentResponse[]>(`/api/v1/incidents?propertyId=${selectedPropertyId}`, { token: auth.token })
      .then(setIncidents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load incidents"));
  }, [auth, selectedPropertyId]);

  useEffect(() => {
    loadIncidents();
  }, [loadIncidents]);

  async function changeStatus(incidentId: number, status: IncidentStatus) {
    if (!auth) return;
    setError(null);
    setUpdatingId(incidentId);
    try {
      await apiFetch(`/api/v1/incidents/${incidentId}/status`, {
        method: "PATCH",
        token: auth.token,
        body: { status },
      });
      loadIncidents();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to update status");
    } finally {
      setUpdatingId(null);
    }
  }

  return (
    <Layout title="Incidents">
      {error && <p className="error">{error}</p>}

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {properties && properties.length > 1 && (
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

      {incidents && incidents.length === 0 && <p className="empty">No incidents reported.</p>}

      {incidents && incidents.length > 0 && (
        <div className="incident-list">
          {incidents.map((incident) => (
            <div key={incident.id} className="incident-card">
              <div className="incident-card-header">
                <span className={`badge severity-${incident.severity.toLowerCase()}`}>{incident.severity}</span>
                <span className={`badge status-${incident.status.toLowerCase()}`}>{incident.status}</span>
                <span className="entry-meta">{new Date(incident.reportedAt).toLocaleString()}</span>
              </div>
              <p>{incident.description}</p>
              <p className="entry-meta">
                Reported by {incident.reportedByGuardName} · {incident.latitude.toFixed(5)}, {incident.longitude.toFixed(5)}
              </p>

              {incident.media.length > 0 && (
                <div className="incident-photos">
                  {incident.media.map((m) => (
                    <IncidentPhoto key={m.id} incidentId={incident.id} mediaId={m.id} token={auth!.token} />
                  ))}
                </div>
              )}

              <label className="incident-status-select">
                Status
                <select
                  value={incident.status}
                  disabled={updatingId === incident.id}
                  onChange={(e) => changeStatus(incident.id, e.target.value as IncidentStatus)}
                >
                  {STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          ))}
        </div>
      )}
    </Layout>
  );
}
