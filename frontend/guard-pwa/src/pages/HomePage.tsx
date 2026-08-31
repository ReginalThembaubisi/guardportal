import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type { OccupancyResponse, ShiftResponse, ShiftScheduleResponse, VisitorCategory, VisitorEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { useOfflineQueue } from "../OfflineQueueContext";
import Layout from "../components/Layout";
import ToleranceBadge from "../components/ToleranceBadge";
import { getCurrentCoordinates } from "../geo";
import { usePatrolStatus, toLocalDateTimeParam } from "../patrol";

const VISITOR_CATEGORIES: VisitorCategory[] = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF"];

function formatShiftLine(shift: ShiftScheduleResponse, dayLabel: string): string {
  const times = shift.startTime && shift.endTime ? ` · ${shift.startTime.slice(0, 5)}–${shift.endTime.slice(0, 5)}` : "";
  return `${dayLabel} · ${shift.shiftType}${times}`;
}

function formatWeekday(shiftDate: string): string {
  return new Date(shiftDate + "T00:00:00").toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
}

/** "Starts in 42 min" / "Starts in 3h 10m" — only meaningful within a 2h window, per design. */
function minutesUntil(shiftDate: string, startTime: string): number {
  const start = new Date(`${shiftDate}T${startTime}`);
  return Math.round((start.getTime() - Date.now()) / 60000);
}

function formatCountdown(minutes: number): string {
  if (minutes <= 0) return "Started";
  if (minutes < 60) return `Starts in ${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `Starts in ${h}h ${m}m`;
}

export default function HomePage() {
  const { auth, setPropertyId, setOpenShift } = useAuth();
  const { pendingClockOut, rejectedClockOut, enqueueAction, dismiss } = useOfflineQueue();
  const [error, setError] = useState<string | null>(null);
  const [queueError, setQueueError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [todayShift, setTodayShift] = useState<ShiftScheduleResponse | null>(null);
  const [upcomingShifts, setUpcomingShifts] = useState<ShiftScheduleResponse[] | null>(null);
  const [occupancy, setOccupancy] = useState<OccupancyResponse | null>(null);
  const [now, setNow] = useState(() => new Date());

  const openShift = auth?.openShift ?? null;
  const patrol = usePatrolStatus(todayShift);

  useEffect(() => {
    if (!auth) return;
    apiFetch<ShiftScheduleResponse | undefined>("/api/v1/shift-schedules/today", { token: auth.token })
      .then((shift) => setTodayShift(shift ?? null))
      .catch(() => {
        // No schedule for today, or offline — clock-in must still work.
      });
    apiFetch<ShiftScheduleResponse[]>("/api/v1/shift-schedules/mine", { token: auth.token })
      .then(setUpcomingShifts)
      .catch(() => {
        // Non-fatal — the roster list just won't show.
      });
  }, [auth]);

  useEffect(() => {
    if (!auth || auth.propertyId === null || !openShift) return;
    apiFetch<OccupancyResponse>(`/api/v1/properties/${auth.propertyId}/occupancy`, { token: auth.token })
      .then(setOccupancy)
      .catch(() => {
        // Non-fatal — the on-site tile falls back to a dash.
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.propertyId, !!openShift]);

  useEffect(() => {
    if (!openShift) return;
    const interval = setInterval(() => setNow(new Date()), 60000);
    return () => clearInterval(interval);
  }, [openShift]);

  async function handleClockIn() {
    if (!auth || pendingClockOut) return;
    setError(null);
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
    setQueueError(null);
    setBusy(true);
    const claimedAt = new Date().toISOString();
    try {
      const coords = await getCurrentCoordinates();
      const idempotencyKey = crypto.randomUUID();
      try {
        await apiFetch<ShiftResponse>("/api/v1/shifts/clock-out", {
          method: "POST",
          token: auth.token,
          body: {
            latitude: coords.latitude,
            longitude: coords.longitude,
            clientClaimedAt: toLocalDateTimeParam(new Date(claimedAt)),
          },
          idempotencyKey,
        });
        setOpenShift(null);
        setOccupancy(null);
      } catch (err) {
        if (err instanceof ApiError) {
          if (err.status === 404) {
            setOpenShift(null);
          } else {
            setError(err.message);
          }
        } else {
          // Network error — queue the clock-out
          try {
            await enqueueAction({
              type: "CLOCK_OUT",
              path: "/api/v1/shifts/clock-out",
              body: {
                latitude: coords.latitude,
                longitude: coords.longitude,
                clientClaimedAt: toLocalDateTimeParam(new Date(claimedAt)),
              },
              clientClaimedAt: claimedAt,
            });
          } catch (qErr) {
            const msg =
              qErr instanceof DOMException && qErr.name === "QuotaExceededError"
                ? "Storage full — free space and try again. Your clock-out was not saved."
                : "Failed to save clock-out locally. Please try again or note the time manually.";
            setQueueError(msg);
          }
        }
      }
    } catch (geoErr) {
      setError(geoErr instanceof Error ? geoErr.message : "Could not get your location");
    } finally {
      setBusy(false);
    }
  }

  const clockTimeLabel = now.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });

  const showCountdown = todayShift?.startTime ? minutesUntil(todayShift.shiftDate, todayShift.startTime) <= 120 : false;

  const lastOnShift: VisitorEntryResponse[] = occupancy
    ? VISITOR_CATEGORIES.flatMap((c) => occupancy.byCategory[c] ?? [])
        .slice()
        .sort((a, b) => new Date(b.enteredAt).getTime() - new Date(a.enteredAt).getTime())
        .slice(0, 2)
    : [];

  const checkpointDueSub = patrol.nextUp
    ? patrol.nextUp.dueAt && patrol.nextUp.dueAt.getTime() < now.getTime()
      ? `${patrol.nextUp.name} overdue`
      : patrol.nextUp.dueAt
        ? `${patrol.nextUp.name} due ${patrol.nextUp.dueAt.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}`
        : patrol.nextUp.name
    : patrol.totalCount > 0
      ? "All checkpoints done"
      : "No route set up";

  return (
    <Layout title="Home">
      <div className="screen-header">
        <div className="screen-header-row">
          <div className="home-left">
            <div className="home-status-row">
              <span className={openShift ? "status-dot on" : "status-dot"} aria-hidden="true" />
              <span className={openShift ? "status-label on" : "status-label"}>{openShift ? "On duty" : "Off duty"}</span>
            </div>
            <span className="home-guard-line">
              {openShift ? `${auth?.fullName} · ${openShift.propertyName}` : auth?.fullName}
            </span>
          </div>
          <span className="screen-subtitle">{clockTimeLabel}</span>
        </div>
      </div>

      <div className="screen-content">
        {error && <p className="error">{error}</p>}
        {queueError && <p className="error">{queueError}</p>}

        {!openShift && (
          <>
            {pendingClockOut ? (
              <div className="ending-shift-card">
                <span className="eyebrow flag">Ending shift</span>
                <p className="ending-shift-body">
                  Your clock-out is saved and will submit when you're back online.
                </p>
                <div className="queue-ts-row" style={{ marginBottom: 4 }}>
                  <span className="queue-ts-label">Clocked out at</span>
                  <span className="queue-ts-value">{new Date(pendingClockOut.clientClaimedAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}</span>
                </div>
                <Link to="/queue" className="ending-shift-link">View outbox</Link>
              </div>
            ) : (
              <>
                {todayShift ? (
                  <div className="next-shift-card">
                    <span className="eyebrow accent">Your next shift</span>
                    <p className="next-shift-property">{todayShift.propertyName}</p>
                    <span className="next-shift-detail">{formatShiftLine(todayShift, "Tonight")}</span>
                    {showCountdown && todayShift.startTime && (
                      <span className="countdown-pill">{formatCountdown(minutesUntil(todayShift.shiftDate, todayShift.startTime))}</span>
                    )}
                    <button className="big-action-button" onClick={handleClockIn} disabled={busy || !!pendingClockOut}>
                      {busy ? "Getting your location…" : "Clock in"}
                    </button>
                    <p className="big-action-caption">Records your location · flagged, never blocked</p>
                  </div>
                ) : (
                  <div className="next-shift-card">
                    <p className="empty" style={{ margin: 0 }}>
                      No shift scheduled for today.
                    </p>
                    <button className="big-action-button" onClick={handleClockIn} disabled={busy || !!pendingClockOut}>
                      {busy ? "Getting your location…" : "Clock in"}
                    </button>
                    <p className="big-action-caption">Records your location · flagged, never blocked</p>
                  </div>
                )}
              </>
            )}

            {upcomingShifts && upcomingShifts.length > 0 && (
              <div>
                <span className="eyebrow">Rest of your week</span>
                <div className="row-list" style={{ marginTop: 8 }}>
                  {upcomingShifts.slice(0, 3).map((shift) => (
                    <div key={shift.id} className="row-list-item">
                      <span className="row-list-item-title">{shift.propertyName}</span>
                      <span className="row-list-item-detail">{formatShiftLine(shift, formatWeekday(shift.shiftDate))}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}

        {openShift && (
          <>
            <div className="current-shift-card">
              <span className="eyebrow">Current shift{openShift.shiftType ? ` · ${openShift.shiftType}` : ""}</span>
              <div className="elapsed-row">
                <span className="elapsed-time">{formatElapsed(openShift.clockInAt, now)}</span>
                <span className="elapsed-since">since {new Date(openShift.clockInAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}</span>
              </div>
              <div style={{ display: "flex" }}>
                <ToleranceBadge withinTolerance={openShift.clockInWithinTolerance} distanceMeters={openShift.clockInDistanceMeters} />
              </div>

              {rejectedClockOut && (
                <div className="rejected-clockout-banner">
                  <span className="rejected-clockout-label">Clock-out rejected</span>
                  <span className="rejected-clockout-reason">{rejectedClockOut.rejectedReason}</span>
                  <div className="rejected-clockout-actions">
                    <button className="danger-outline-button" onClick={handleClockOut} disabled={busy}>
                      {busy ? "Getting your location…" : "Clock out"}
                    </button>
                    <button
                      className="link-button"
                      style={{ fontSize: 12 }}
                      onClick={() => dismiss(rejectedClockOut.id)}
                    >
                      Dismiss
                    </button>
                  </div>
                  <Link to="/queue" className="ending-shift-link" style={{ marginTop: 4 }}>View outbox</Link>
                </div>
              )}

              {!rejectedClockOut && (
                <button className="danger-outline-button" onClick={handleClockOut} disabled={busy}>
                  {busy ? "Getting your location…" : "Clock out"}
                </button>
              )}
            </div>

            <div className="count-strip">
              <div className="count-tile">
                <span className="count-tile-number accent">{occupancy ? occupancy.totalOnSite : "—"}</span>
                <span className="count-tile-label">On site</span>
              </div>
              <div className="count-tile">
                <span className="count-tile-number">
                  {patrol.loading ? "—" : patrol.scannedCount}
                  {!patrol.loading && patrol.totalCount > 0 && <span className="count-tile-number-sub">/{patrol.totalCount}</span>}
                </span>
                <span className="count-tile-label">Checkpoints</span>
              </div>
              <div className={patrol.missedCount > 0 ? "count-tile flagged" : "count-tile"}>
                <span className="count-tile-number">{patrol.loading ? "—" : patrol.missedCount}</span>
                <span className="count-tile-label">Missed</span>
              </div>
            </div>

            <div>
              <span className="eyebrow">Do now</span>
              <div style={{ display: "flex", flexDirection: "column", gap: 9, marginTop: 8 }}>
                <Link to="/gate" className="primary-action">
                  <span className="primary-action-title">Check in a visitor</span>
                  <span className="primary-action-sub">Scan QR · or type the code</span>
                </Link>
                <div className="secondary-pair">
                  <Link to="/walk-in" className="secondary-action">
                    <span className="secondary-action-title">Walk-in</span>
                    <span className="secondary-action-sub">No invite</span>
                  </Link>
                  <Link to="/patrol" className="secondary-action">
                    <span className="secondary-action-title">Checkpoint</span>
                    <span className={patrol.nextUp?.status === "danger" ? "secondary-action-sub flag" : "secondary-action-sub"}>
                      {checkpointDueSub}
                    </span>
                  </Link>
                </div>
              </div>
            </div>

            {lastOnShift.length > 0 && (
              <div>
                <span className="eyebrow">Last on this shift</span>
                <div style={{ marginTop: 8 }}>
                  {lastOnShift.map((entry) => (
                    <div key={entry.id} className="last-shift-row">
                      <span className="last-shift-subject">
                        {entry.visitorName}
                        {entry.vehicleRecognized && <span className="last-shift-qualifier ok"> · recognised</span>}
                      </span>
                      <span className="last-shift-time">{new Date(entry.enteredAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })} in</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {!openShift && (
        <div className="off-duty-strip">
          <span className="off-duty-strip-dot" aria-hidden="true" />
          <span className="off-duty-strip-text">Clock in to record entries or scans</span>
        </div>
      )}
    </Layout>
  );
}

function formatElapsed(clockInAt: string, now: Date): string {
  const ms = now.getTime() - new Date(clockInAt).getTime();
  const totalMinutes = Math.max(0, Math.floor(ms / 60000));
  const h = Math.floor(totalMinutes / 60);
  const m = totalMinutes % 60;
  return `${h}h ${m}m`;
}
