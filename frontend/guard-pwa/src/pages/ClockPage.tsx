import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { ShiftResponse, ShiftScheduleResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import ToleranceBadge from "../components/ToleranceBadge";
import { getCurrentCoordinates } from "../geo";

export default function ClockPage() {
  const { auth, setPropertyId, setOpenShift } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [lastClockOut, setLastClockOut] = useState<ShiftResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [todayShift, setTodayShift] = useState<ShiftScheduleResponse | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<ShiftScheduleResponse | undefined>("/api/v1/shift-schedules/today", { token: auth.token })
      .then((shift) => setTodayShift(shift ?? null))
      .catch(() => {
        // No schedule set up yet, or offline — clock-in still works without it.
      });
  }, [auth]);

  async function handleClockIn() {
    if (!auth) return;
    setError(null);
    setLastClockOut(null);
    setBusy(true);
    try {
      const coords = await getCurrentCoordinates();
      const shift = await apiFetch<ShiftResponse>("/api/v1/shifts", {
        method: "POST",
        token: auth.token,
        body: { latitude: coords.latitude, longitude: coords.longitude },
      });
      setOpenShift(shift);
      setPropertyId(shift.propertyId);
    } catch (err) {
      if (err instanceof ApiError && err.message.toLowerCase().includes("already have an open shift")) {
        // Local state didn't know about it — a race with AuthContext's own
        // refresh, or that refresh failed (e.g. offline) right before this
        // tap. Fetch the real thing instead of guessing at it.
        try {
          const shift = await apiFetch<ShiftResponse>("/api/v1/shifts/current", { token: auth.token });
          setOpenShift(shift);
          setPropertyId(shift.propertyId);
        } catch {
          setError("You already have an open shift, but its details couldn't be loaded — try again.");
        }
      } else {
        setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : "Clock-in failed");
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleClockOut() {
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      const coords = await getCurrentCoordinates();
      const shift = await apiFetch<ShiftResponse>("/api/v1/shifts/clock-out", {
        method: "POST",
        token: auth.token,
        body: { latitude: coords.latitude, longitude: coords.longitude },
      });
      setLastClockOut(shift);
      setOpenShift(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        // Local state thought a shift was open but the server disagrees —
        // trust the server and reset.
        setOpenShift(null);
      }
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : "Clock-out failed");
    } finally {
      setBusy(false);
    }
  }

  const openShift = auth?.openShift ?? null;

  return (
    <Layout title="Clock In/Out">
      {error && <p className="error">{error}</p>}

      {!openShift && (
        <div className="clock-card">
          {todayShift ? (
            <p className="clock-status">
              Today's shift: {todayShift.propertyName} — {todayShift.shiftType}
              {todayShift.startTime && todayShift.endTime && ` (${todayShift.startTime}–${todayShift.endTime})`}
            </p>
          ) : (
            <p className="empty">You're not clocked in.</p>
          )}
          <button className="clock-button" onClick={handleClockIn} disabled={busy}>
            {busy ? "Getting your location…" : "Clock In"}
          </button>
        </div>
      )}

      {openShift && (
        <div className="clock-card">
          <p className="clock-status">
            Clocked in since {new Date(openShift.clockInAt).toLocaleTimeString()}
          </p>
          <ToleranceBadge
            withinTolerance={openShift.clockInWithinTolerance}
            distanceMeters={openShift.clockInDistanceMeters}
          />
          <button className="clock-button clock-out" onClick={handleClockOut} disabled={busy}>
            {busy ? "Getting your location…" : "Clock Out"}
          </button>
        </div>
      )}

      {lastClockOut && (
        <div className="clock-card clock-summary">
          <h2>Shift ended</h2>
          <p className="entry-meta">
            Clocked in {new Date(lastClockOut.clockInAt).toLocaleTimeString()} · clocked out{" "}
            {lastClockOut.clockOutAt && new Date(lastClockOut.clockOutAt).toLocaleTimeString()}
          </p>
          <ToleranceBadge
            withinTolerance={lastClockOut.clockOutWithinTolerance}
            distanceMeters={lastClockOut.clockOutDistanceMeters}
          />
        </div>
      )}
    </Layout>
  );
}
