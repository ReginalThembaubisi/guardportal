import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { GuardResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function GuardsPage() {
  const { auth } = useAuth();
  const [guards, setGuards] = useState<GuardResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<GuardResponse[]>("/api/v1/guards", { token: auth.token })
      .then(setGuards)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load guards"));
  }, [auth]);

  function gradeChip(grade: string | null) {
    if (!grade) return null;
    return <span className="psira-grade-chip">Grade {grade}</span>;
  }

  function expiryLabel(expiry: string | null) {
    if (!expiry) return null;
    const date = new Date(expiry);
    const expired = date < new Date();
    return (
      <span className={expired ? "psira-expiry expired" : "psira-expiry"}>
        exp {date.toLocaleDateString("en-ZA", { year: "numeric", month: "short" })}
      </span>
    );
  }

  return (
    <Layout title="Guards">
      {error && <p className="error">{error}</p>}
      {guards && guards.length === 0 && (
        <p className="empty">No guards yet — add one from the Add Guard screen.</p>
      )}
      {guards && guards.length > 0 && (
        <table className="entries-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Property</th>
              <th>Badge</th>
              <th>PSIRA</th>
              <th>Email</th>
            </tr>
          </thead>
          <tbody>
            {guards.map((g) => (
              <tr key={g.id}>
                <td>
                  {g.fullName}
                  {g.psiraGrade && <> {gradeChip(g.psiraGrade)}</>}
                </td>
                <td>{g.propertyName}</td>
                <td>{g.badgeNumber ?? <span className="text-muted">—</span>}</td>
                <td>
                  {g.psiraNumber ?? <span className="text-muted">—</span>}
                  {g.psiraExpiry && <> {expiryLabel(g.psiraExpiry)}</>}
                </td>
                <td>{g.email}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
