import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { ShiftScheduleResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

function formatShift(shift: ShiftScheduleResponse): string {
  const date = new Date(shift.shiftDate + "T00:00:00").toLocaleDateString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
  });
  const times = shift.startTime && shift.endTime ? ` · ${shift.startTime}–${shift.endTime}` : "";
  return `${date} · ${shift.shiftType}${times}`;
}

export default function MyShiftsPage() {
  const { auth } = useAuth();
  const [shifts, setShifts] = useState<ShiftScheduleResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<ShiftScheduleResponse[]>("/api/v1/shift-schedules/mine", { token: auth.token })
      .then(setShifts)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your shifts"));
  }, [auth]);

  return (
    <Layout title="My Shifts">
      {error && <p className="error">{error}</p>}

      {shifts && shifts.length === 0 && <p className="empty">No upcoming shifts have been scheduled for you yet.</p>}

      {shifts && shifts.length > 0 && (
        <ul>
          {shifts.map((shift) => (
            <li key={shift.id}>
              <strong>{shift.propertyName}</strong>
              <span className="entry-meta">{formatShift(shift)}</span>
            </li>
          ))}
        </ul>
      )}
    </Layout>
  );
}
