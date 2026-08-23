import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { InvitationResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function PendingInvitationsPage() {
  const { auth } = useAuth();
  const [invitations, setInvitations] = useState<InvitationResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<InvitationResponse[]>("/api/v1/invitations", { token: auth.token })
      .then(setInvitations)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load invitations"));
  }, [auth]);

  const pending = invitations?.filter((inv) => inv.status === "PENDING") ?? null;

  return (
    <Layout title="Pending Invitations">
      {error && <p className="error">{error}</p>}

      {pending && pending.length === 0 && (
        <p className="empty">No pending invitations. Create one from "New Invitation".</p>
      )}

      {pending && pending.length > 0 && (
        <table className="entries-table">
          <thead>
            <tr>
              <th>Visitor</th>
              <th>Purpose</th>
              <th>Valid from</th>
              <th>Valid until</th>
              <th>Check-in link</th>
            </tr>
          </thead>
          <tbody>
            {pending.map((inv) => (
              <tr key={inv.id}>
                <td>{inv.visitorName}</td>
                <td>{inv.purpose ?? "—"}</td>
                <td>{new Date(inv.validFrom).toLocaleString()}</td>
                <td>{new Date(inv.validUntil).toLocaleString()}</td>
                <td>
                  <a href={inv.checkInUrl} target="_blank" rel="noreferrer">
                    Link
                  </a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
