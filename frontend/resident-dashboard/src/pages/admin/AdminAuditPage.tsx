import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../../api/client";
import type { AuditVerificationResponse } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Layout from "../../components/Layout";

export default function AdminAuditPage() {
  const { auth } = useAuth();
  const [result, setResult] = useState<AuditVerificationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function verify() {
    if (!auth) return;
    setError(null);
    setBusy(true);
    apiFetch<AuditVerificationResponse>("/api/v1/audit/verify", { token: auth.token })
      .then(setResult)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to verify audit chain"))
      .finally(() => setBusy(false));
  }

  useEffect(verify, [auth]);

  return (
    <Layout title="Admin — Audit Chain">
      {error && <p className="error">{error}</p>}

      <button type="button" onClick={verify} disabled={busy}>
        {busy ? "Verifying…" : "Re-verify"}
      </button>

      {result && (
        <div style={{ marginTop: 20 }}>
          {result.valid ? (
            <p className="dev-hint">✓ Audit chain is intact — every hash matches.</p>
          ) : (
            <p className="error">
              ✗ Audit chain is broken, starting at audit_log id {result.firstBrokenId}. The hash chain no longer
              matches from this row onward — investigate immediately.
            </p>
          )}
        </div>
      )}
    </Layout>
  );
}
