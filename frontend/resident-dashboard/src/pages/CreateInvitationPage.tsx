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

/** 417302 -> "417 302" — the triple grouping is how the code is spoken. */
function formatCode(code: string): string {
  return code.length > 3 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;
}

/**
 * The outgoing WhatsApp text, read back out of the link itself rather than
 * rebuilt separately — showing the resident anything other than the actual
 * bytes their visitor receives would undercut the point of previewing it.
 */
function extractMessagePreview(whatsappShareLink: string): string {
  try {
    return new URL(whatsappShareLink).searchParams.get("text") ?? "";
  } catch {
    return "";
  }
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
  const [codeCopied, setCodeCopied] = useState(false);

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
    setCodeCopied(false);
    setVisitorName("");
    setVisitorPhone("");
    setPurpose("");
    const freshNow = new Date();
    setValidFrom(toLocalInputValue(freshNow));
    setValidUntil(toLocalInputValue(new Date(freshNow.getTime() + 24 * 60 * 60 * 1000)));
  }

  async function copyCode() {
    if (!invitation) return;
    try {
      await navigator.clipboard.writeText(invitation.shortCode);
      setCodeCopied(true);
      setTimeout(() => setCodeCopied(false), 1500);
    } catch {
      // Clipboard access can be denied by the browser — the code is still
      // right there on screen to read out or type manually.
    }
  }

  if (invitation) {
    const visitorFirstName = invitation.visitorName.trim().split(/\s+/)[0] || invitation.visitorName;
    const qrFileName = `checkin-qr-${invitation.id}.png`;

    return (
      <Layout title="Invite a Visitor">
        <div className="share-screen">
          <h2 className="share-visitor-name">{invitation.visitorName}</h2>
          <p className="share-window">
            Valid {new Date(invitation.validFrom).toLocaleString()} – {new Date(invitation.validUntil).toLocaleString()}
          </p>

          <div className="share-qr-card">
            {invitation.qrCodeDataUri && (
              <img className="share-qr-image" src={invitation.qrCodeDataUri} alt="Check-in QR code" />
            )}
            <span className="share-divider">Or read this out</span>
            <div className="share-code-block">
              <span className="share-code">{formatCode(invitation.shortCode)}</span>
              <span className="share-code-caption">Works even if the camera fails</span>
            </div>
          </div>

          {invitation.whatsappShareLink && (
            <div className="share-message-card">
              <span className="share-message-eyebrow">What {visitorFirstName} receives</span>
              <p className="share-message-body">{extractMessagePreview(invitation.whatsappShareLink)}</p>
            </div>
          )}

          {invitation.whatsappShareLink && (
            <a className="share-whatsapp-button" href={invitation.whatsappShareLink} target="_blank" rel="noreferrer">
              Send via WhatsApp
            </a>
          )}

          <div className="share-action-row">
            <button type="button" onClick={copyCode}>
              {codeCopied ? "Copied" : "Copy code"}
            </button>
            {invitation.qrCodeDataUri && (
              <a href={invitation.qrCodeDataUri} download={qrFileName}>
                Save QR
              </a>
            )}
          </div>

          <p className="share-closing-note">
            The code and the link are two doors into the same invitation. Either one checks {visitorFirstName} in
            once — using one closes both.
          </p>

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
