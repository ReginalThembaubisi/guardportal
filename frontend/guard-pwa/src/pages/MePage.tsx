import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type { ShiftScheduleResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

function formatShiftLine(shift: ShiftScheduleResponse, dayLabel: string): string {
  const times = shift.startTime && shift.endTime ? ` · ${shift.startTime.slice(0, 5)}–${shift.endTime.slice(0, 5)}` : "";
  return `${dayLabel} · ${shift.shiftType}${times}`;
}

function formatWeekday(shiftDate: string): string {
  return new Date(shiftDate + "T00:00:00").toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
}

/** Roster, personal history, log out — the low-frequency items, given an honest home instead of a hamburger. */
export default function MePage() {
  const { auth, logout } = useAuth();
  const navigate = useNavigate();
  const [shifts, setShifts] = useState<ShiftScheduleResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<ShiftScheduleResponse[]>("/api/v1/shift-schedules/mine", { token: auth.token })
      .then(setShifts)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your shifts"));
  }, [auth]);

  function handleLogout() {
    logout();
    navigate("/");
  }

  const todayDate = new Date().toLocaleDateString("en-CA"); // YYYY-MM-DD, local

  return (
    <Layout title="Me">
      <div className="screen-header">
        <h1 className="screen-title">{auth?.fullName}</h1>
        <span className="screen-subtitle">
          Guard{auth?.openShift ? ` · ${auth.openShift.propertyName}` : ""}
        </span>
      </div>

      <div className="screen-content">
        {error && <p className="error">{error}</p>}

        <div>
          <span className="eyebrow">My shifts</span>
          <div className="row-list" style={{ marginTop: 8 }}>
            {shifts && shifts.length === 0 && <p className="empty" style={{ padding: 14 }}>No upcoming shifts have been scheduled for you yet.</p>}
            {shifts?.map((shift) => {
              const isCurrent = !!auth?.openShift && shift.shiftDate === todayDate;
              return (
                <div key={shift.id} className={isCurrent ? "row-list-item current" : "row-list-item"}>
                  <span className="row-list-item-title">
                    {shift.propertyName}
                    {isCurrent && <span style={{ color: "var(--ok)" }}> · on now</span>}
                  </span>
                  <span className="row-list-item-detail">{formatShiftLine(shift, formatWeekday(shift.shiftDate))}</span>
                </div>
              );
            })}
          </div>
        </div>

        <div className="row-list">
          <Link to="/vehicle-history" className="action-list-item">
            Vehicle history
          </Link>
          <button type="button" className="action-list-item" style={{ color: "var(--danger)" }} onClick={handleLogout}>
            Log out
          </button>
        </div>
      </div>
    </Layout>
  );
}
