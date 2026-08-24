import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { VisitorHistoryForResidentResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import Seal from "../components/Seal";

export default function ResidentHistoryPage() {
  const { auth } = useAuth();
  const [entries, setEntries] = useState<VisitorHistoryForResidentResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<VisitorHistoryForResidentResponse[]>("/api/v1/visitor-entries/mine", { token: auth.token })
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
                    <>
                      {" "}
                      <Seal state="cleared">
                        Recognized{entry.recognizedVehicleOwnerName && ` — ${entry.recognizedVehicleOwnerName}'s`}
                      </Seal>
                    </>
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
