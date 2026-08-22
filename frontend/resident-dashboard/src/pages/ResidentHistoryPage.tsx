import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { VisitorEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

export default function ResidentHistoryPage() {
  const { auth } = useAuth();
  const [entries, setEntries] = useState<VisitorEntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<VisitorEntryResponse[]>("/api/v1/visitor-entries/mine", { token: auth.token })
      .then(setEntries)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load visitor history"));
  }, [auth]);

  return (
    <Layout title="My Visitor History">
      {error && <p className="error">{error}</p>}
      {!entries && !error && <p>Loading…</p>}
      {entries && entries.length === 0 && <p className="empty">No visitors yet. Invitations you create will show up here once they check in.</p>}
      {entries && entries.length > 0 && (
        <table className="entries-table">
          <thead>
            <tr>
              <th>Visitor</th>
              <th>Category</th>
              <th>Vehicle</th>
              <th>Entered</th>
              <th>Exited</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.id}>
                <td>{entry.visitorName}</td>
                <td>{entry.category}</td>
                <td>
                  {entry.vehicleRegistration ?? "—"}
                  {entry.vehicleRegistration && entry.vehicleRecognized && (
                    <span className="badge recognized"> recognized</span>
                  )}
                </td>
                <td>{new Date(entry.enteredAt).toLocaleString()}</td>
                <td>{entry.exitedAt ? new Date(entry.exitedAt).toLocaleString() : <span className="badge on-site">on site</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
