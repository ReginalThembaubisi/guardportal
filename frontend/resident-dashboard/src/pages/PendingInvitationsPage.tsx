import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { InvitationResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

const ONE_HOUR_MS = 60 * 60 * 1000;

/** 417302 -> "417 302" — the triple grouping is how the code is spoken. */
function formatCode(code: string): string {
  return code.length > 3 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;
}

/** currently valid, not yet started, or expiring within the hour — each gets its own left-border colour. */
function rowState(inv: InvitationResponse): "valid-now" | "not-started" | "expiring-soon" {
  const now = Date.now();
  const from = new Date(inv.validFrom).getTime();
  const until = new Date(inv.validUntil).getTime();
  if (now < from) return "not-started";
  if (until - now <= ONE_HOUR_MS) return "expiring-soon";
  return "valid-now";
}

export default function PendingInvitationsPage() {
  const { auth } = useAuth();
  const [invitations, setInvitations] = useState<InvitationResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [resendingId, setResendingId] = useState<number | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<InvitationResponse[]>("/api/v1/invitations", { token: auth.token })
      .then(setInvitations)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load invitations"));
  }, [auth]);

  const pending = invitations?.filter((inv) => inv.status === "PENDING") ?? null;

  async function copyCode(invitationId: number, code: string) {
    try {
      await navigator.clipboard.writeText(code);
      setCopiedId(invitationId);
      setTimeout(() => setCopiedId((id) => (id === invitationId ? null : id)), 1500);
    } catch {
      // Clipboard access can be denied by the browser — the code is still visible to read out.
    }
  }

  async function sendAgain(invitationId: number) {
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
    <Layout title="Active">
      {error && <p className="error">{error}</p>}

      {pending && pending.length === 0 && (
        <p className="empty">No pending invitations. Create one from "Invite".</p>
      )}

      {pending && pending.length > 0 && (
        <div>
          {pending.map((inv) => (
            <div className={`active-row ${rowState(inv)}`} key={inv.id}>
              <div className="active-row-main">
                <div className="active-row-name-line">
                  {inv.visitorName}
                  {inv.purpose && <span className="active-row-purpose"> · {inv.purpose}</span>}
                </div>
                <div className="active-row-window">
                  {new Date(inv.validFrom).toLocaleString()} – {new Date(inv.validUntil).toLocaleString()}
                </div>
              </div>
              <div className="active-row-code-col">
                <span className="active-row-code">{formatCode(inv.shortCode)}</span>
                <div className="active-row-actions">
                  <button type="button" onClick={() => copyCode(inv.id, inv.shortCode)}>
                    {copiedId === inv.id ? "Copied" : "Copy"}
                  </button>
                  <button type="button" onClick={() => sendAgain(inv.id)} disabled={resendingId === inv.id}>
                    {resendingId === inv.id ? "…" : "Send again"}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </Layout>
  );
}
