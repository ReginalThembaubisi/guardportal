import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type {
  InvitationResponse,
  ResidentProfileResponse,
  VehicleResponse,
  VisitorHistoryForResidentResponse,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import Seal from "../components/Seal";

/** 417302 -> "417 302" — the triple grouping is how the code is spoken. */
function formatCode(code: string): string {
  return code.length > 3 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;
}

export default function ResidentHomePage() {
  const { auth } = useAuth();
  const [profile, setProfile] = useState<ResidentProfileResponse | null>(null);
  const [invitations, setInvitations] = useState<InvitationResponse[] | null>(null);
  const [visits, setVisits] = useState<VisitorHistoryForResidentResponse[] | null>(null);
  const [vehicles, setVehicles] = useState<VehicleResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [resendingId, setResendingId] = useState<number | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<ResidentProfileResponse>("/api/v1/residents/me", { token: auth.token }).then(setProfile).catch(() => {
      // Non-fatal — the header just falls back to the account name alone.
    });
    apiFetch<InvitationResponse[]>("/api/v1/invitations", { token: auth.token })
      .then(setInvitations)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load invitations"));
    apiFetch<VisitorHistoryForResidentResponse[]>("/api/v1/visitor-entries/mine", { token: auth.token })
      .then(setVisits)
      .catch(() => {
        // Non-fatal — "Recent visits" just won't show.
      });
    apiFetch<VehicleResponse[]>("/api/v1/vehicles", { token: auth.token })
      .then(setVehicles)
      .catch(() => {
        // Non-fatal — "My cars" just won't show.
      });
  }, [auth]);

  const now = Date.now();
  const expectedNow =
    invitations?.filter(
      (inv) => inv.status === "PENDING" && new Date(inv.validFrom).getTime() <= now && new Date(inv.validUntil).getTime() >= now,
    ) ?? [];

  async function copyCode(invitationId: number, code: string) {
    try {
      await navigator.clipboard.writeText(code);
      setCopiedId(invitationId);
      setTimeout(() => setCopiedId((id) => (id === invitationId ? null : id)), 1500);
    } catch {
      // Clipboard access can be denied by the browser — the code is still visible to read out.
    }
  }

  async function resend(invitationId: number) {
    if (!auth) return;
    setResendingId(invitationId);
    try {
      const fresh = await apiFetch<InvitationResponse>(`/api/v1/invitations/${invitationId}`, { token: auth.token });
      if (fresh.whatsappShareLink) {
        window.open(fresh.whatsappShareLink, "_blank", "noreferrer");
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to re-send invitation");
    } finally {
      setResendingId(null);
    }
  }

  return (
    <Layout title="Home">
      <div className="home-header">
        <h2 className="home-header-name">{profile?.fullName ?? auth?.fullName}</h2>
        {profile && (
          <p className="home-header-unit">
            Unit {profile.unitNumber} · {profile.propertyName}
          </p>
        )}
      </div>

      {error && <p className="error">{error}</p>}

      <Link to="/invite" className="home-primary-action">
        <span className="home-primary-action-title">Invite a visitor</span>
        <span className="home-primary-action-sub">Sends a code and a QR</span>
      </Link>

      {expectedNow.length > 0 && (
        <div className="home-section">
          <span className="home-section-eyebrow">Expected now</span>
          {expectedNow.map((inv) => (
            <div className="expected-now-card" key={inv.id}>
              <div className="expected-now-top">
                <span className="expected-now-name">{inv.visitorName}</span>
                <span className="not-arrived-pill">Not arrived</span>
              </div>
              <p className="expected-now-window">
                Valid {new Date(inv.validFrom).toLocaleString()} – {new Date(inv.validUntil).toLocaleString()}
              </p>
              <div className="expected-now-code-row">
                <span className="expected-now-code">{formatCode(inv.shortCode)}</span>
                <button type="button" className="expected-now-copy" onClick={() => copyCode(inv.id, inv.shortCode)}>
                  {copiedId === inv.id ? "Copied" : "Copy"}
                </button>
              </div>
              <button type="button" className="expected-now-resend" onClick={() => resend(inv.id)} disabled={resendingId === inv.id}>
                {resendingId === inv.id ? "Preparing…" : "Send code & QR again"}
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="home-section">
        <div className="home-section-header">
          <span className="home-section-eyebrow" style={{ marginBottom: 0 }}>
            Recent visits
          </span>
          <Link to="/history" className="link-button">
            See all
          </Link>
        </div>
        {visits && visits.length === 0 && <p className="empty">No visitors yet.</p>}
        {visits && visits.length > 0 && (
          <div>
            {visits.slice(0, 3).map((v) => (
              <div className="recent-visit-row" key={v.id}>
                <span className="recent-visit-name">
                  {v.visitorName} <span className="recent-visit-category">{v.category}</span>
                </span>
                <span className="recent-visit-meta">
                  {new Date(v.enteredAt).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })}
                  {" · "}
                  {new Date(v.enteredAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })} in
                  {v.exitedAt && ` · ${new Date(v.exitedAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })} out`}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="home-section">
        <span className="home-section-eyebrow">My cars</span>
        {vehicles && vehicles.length === 0 && <p className="empty">No vehicles registered yet.</p>}
        {vehicles && vehicles.length > 0 && (
          <div>
            {vehicles.map((v) => (
              <div className="my-car-row" key={v.id}>
                <span className="my-car-reg">{v.registration}</span>
                <Seal state="cleared">Registered</Seal>
                <Link to="/vehicles" className="link-button">
                  Manage
                </Link>
              </div>
            ))}
          </div>
        )}
      </div>
    </Layout>
  );
}
