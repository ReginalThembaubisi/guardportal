import { useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { InvitationResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in local time — no timezone suffix. */
function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function CreateInvitationPage() {
  const { auth } = useAuth();
  const now = new Date();
  const in24h = new Date(now.getTime() + 24 * 60 * 60 * 1000);

  const [visitorName, setVisitorName] = useState("");
  const [visitorPhone, setVisitorPhone] = useState("");
  const [purpose, setPurpose] = useState("");
  const [validFrom, setValidFrom] = useState(toLocalInputValue(now));
  const [validUntil, setValidUntil] = useState(toLocalInputValue(in24h));
  const [error, setError] = useState<string | null>(null);
  const [invitation, setInvitation] = useState<InvitationResponse | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      const created = await apiFetch<InvitationResponse>("/api/v1/invitations", {
        method: "POST",
        token: auth.token,
        body: {
          visitorName: visitorName.trim(),
          visitorPhone: visitorPhone.trim() || undefined,
          purpose: purpose.trim() || undefined,
          validFrom,
          validUntil,
        },
      });
      setInvitation(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create invitation");
    } finally {
      setBusy(false);
    }
  }

  function startOver() {
    setInvitation(null);
    setVisitorName("");
    setVisitorPhone("");
    setPurpose("");
    const freshNow = new Date();
    setValidFrom(toLocalInputValue(freshNow));
    setValidUntil(toLocalInputValue(new Date(freshNow.getTime() + 24 * 60 * 60 * 1000)));
  }

  if (invitation) {
    return (
      <Layout title="Invite a Visitor">
        <div className="invitation-result">
          <h2>Invitation created</h2>
          <p className="checkin-visitor-name">{invitation.visitorName}</p>
          <p className="entry-meta">
            Valid {new Date(invitation.validFrom).toLocaleString()} – {new Date(invitation.validUntil).toLocaleString()}
          </p>

          {invitation.qrCodeDataUri && (
            <img className="invitation-qr" src={invitation.qrCodeDataUri} alt="Check-in QR code" />
          )}

          <p className="entry-meta">Show this QR code to the guard at the gate, or share the link below.</p>

          {invitation.whatsappShareLink && (
            <a
              className="whatsapp-share-button"
              href={invitation.whatsappShareLink}
              target="_blank"
              rel="noreferrer"
            >
              Share via WhatsApp
            </a>
          )}

          <button className="link-button" onClick={startOver}>
            Create another invitation
          </button>
        </div>
      </Layout>
    );
  }

  return (
    <Layout title="Invite a Visitor">
      {error && <p className="error">{error}</p>}

      <form onSubmit={submit}>
        <label>
          Visitor name
          <input type="text" value={visitorName} onChange={(e) => setVisitorName(e.target.value)} required autoFocus />
        </label>
        <label>
          Visitor phone (optional)
          <input
            type="tel"
            value={visitorPhone}
            onChange={(e) => setVisitorPhone(e.target.value)}
            placeholder="+27821234567"
          />
        </label>
        <label>
          Purpose (optional)
          <input
            type="text"
            value={purpose}
            onChange={(e) => setPurpose(e.target.value)}
            placeholder="e.g. Family visit"
          />
        </label>
        <label>
          Valid from
          <input type="datetime-local" value={validFrom} onChange={(e) => setValidFrom(e.target.value)} required />
        </label>
        <label>
          Valid until
          <input type="datetime-local" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} required />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Creating…" : "Create invitation"}
        </button>
      </form>
    </Layout>
  );
}
