import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { CheckpointResponse, CheckpointScanResponse, ShiftScheduleResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import { getCurrentCoordinates } from "../geo";
import { usePatrolStatus } from "../patrol";

export default function PatrolPage() {
  const { auth } = useAuth();
  const [todayShift, setTodayShift] = useState<ShiftScheduleResponse | null>(null);
  const [checkpoints, setCheckpoints] = useState<CheckpointResponse[] | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [selectedCheckpointId, setSelectedCheckpointId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [lastScan, setLastScan] = useState<CheckpointScanResponse | null>(null);

  const patrol = usePatrolStatus(todayShift);
  const openShift = auth?.openShift ?? null;

  useEffect(() => {
    if (!auth) return;
    apiFetch<ShiftScheduleResponse | undefined>("/api/v1/shift-schedules/today", { token: auth.token })
      .then((shift) => setTodayShift(shift ?? null))
      .catch(() => {});
  }, [auth]);

  useEffect(() => {
    if (!auth || auth.propertyId === null) return;
    apiFetch<CheckpointResponse[]>(`/api/v1/checkpoints?propertyId=${auth.propertyId}`, { token: auth.token })
      .then((list) => {
        setCheckpoints(list);
        setSelectedCheckpointId((prev) => (prev !== null && list.some((c) => c.id === prev) ? prev : list[0]?.id ?? null));
      })
      .catch(() => {});
  }, [auth]);

  async function checkInAt(checkpointId: number) {
    if (!auth || busy) return;
    setError(null);
    setBusy(true);
    try {
      const coords = await getCurrentCoordinates();
      const scan = await apiFetch<CheckpointScanResponse>("/api/v1/checkpoint-scans", {
        method: "POST",
        token: auth.token,
        body: { checkpointId, latitude: coords.latitude, longitude: coords.longitude },
      });
      setLastScan(scan);
      setPickerOpen(false);
      patrol.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : "Check-in failed");
    } finally {
      setBusy(false);
    }
  }

  function handleNextUp() {
    if (patrol.nextUp) checkInAt(patrol.nextUp.checkpointId);
  }

  function handlePickerSubmit(e: FormEvent) {
    e.preventDefault();
    if (selectedCheckpointId !== null) checkInAt(selectedCheckpointId);
  }

  const progressPct = patrol.totalCount > 0 ? Math.round((patrol.scannedCount / patrol.totalCount) * 100) : 0;
  const nextUpDue = patrol.nextUp?.dueAt ?? null;
  const nextUpOverdue = nextUpDue ? nextUpDue.getTime() < Date.now() : false;

  return (
    <Layout title="Patrol">
      <div className="screen-header">
        <div className="screen-header-row">
          <h1 className="screen-title" style={{ fontSize: 23 }}>
            Patrol
          </h1>
          <span className="screen-subtitle">{patrol.routeName ?? ""}</span>
        </div>
        <div className="progress-row">
          <div className="progress-track">
            <div className="progress-fill" style={{ width: `${progressPct}%` }} />
          </div>
          <span className="progress-count">
            {patrol.scannedCount} / {patrol.totalCount}
          </span>
        </div>
      </div>

      <div className="screen-content tight">
        {!openShift && (
          <p className="dev-hint">You don't look clocked in — checkpoint check-ins require an open shift.</p>
        )}
        {error && <p className="error">{error}</p>}
        {patrol.error && <p className="error">{patrol.error}</p>}

        {patrol.totalCount === 0 && !patrol.loading && (
          <p className="empty">No patrol route has been set up for your property yet.</p>
        )}

        {patrol.nextUp ? (
          <div className={nextUpOverdue ? "next-up-card due" : "next-up-card"}>
            <span className={nextUpOverdue ? "eyebrow flag" : "eyebrow"}>
              Next up{nextUpDue && ` · ${nextUpOverdue ? "overdue" : `due ${nextUpDue.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}`}`}
            </span>
            <p className="next-up-name">{patrol.nextUp.name}</p>
            <button className="big-action-button" onClick={handleNextUp} disabled={busy || !openShift}>
              {busy ? "Getting your location…" : "Check in here"}
            </button>
            <button type="button" className="next-up-secondary" onClick={() => setPickerOpen((v) => !v)}>
              Pick a different checkpoint
            </button>
          </div>
        ) : (
          patrol.totalCount > 0 && (
            <div className="next-up-card" style={{ borderColor: "var(--ok)" }}>
              <span className="eyebrow" style={{ color: "var(--ok)" }}>
                Route complete
              </span>
              <p className="next-up-name">All checkpoints scanned this shift</p>
              <button type="button" className="next-up-secondary" onClick={() => setPickerOpen((v) => !v)}>
                Pick a different checkpoint
              </button>
            </div>
          )
        )}

        {pickerOpen && checkpoints && checkpoints.length > 0 && (
          <form onSubmit={handlePickerSubmit}>
            <label>
              Checkpoint
              <select value={selectedCheckpointId ?? ""} onChange={(e) => setSelectedCheckpointId(Number(e.target.value))} required>
                {checkpoints.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <button type="submit" disabled={busy || !openShift}>
              {busy ? "Getting your location…" : "Check in"}
            </button>
          </form>
        )}

        {lastScan && (
          <div className="checkin-result">
            <h2>Checked in</h2>
            <p className="checkin-visitor-name">{lastScan.checkpointName}</p>
            <p className="entry-meta">{new Date(lastScan.scannedAt).toLocaleTimeString()}</p>
          </div>
        )}

        {patrol.stops.length > 0 && (
          <div>
            <span className="eyebrow">This shift</span>
            <div className="row-list" style={{ marginTop: 8 }}>
              {patrol.stops.map((stop) => (
                <div key={stop.checkpointId} className={`this-shift-row ${stop.status}`}>
                  <span className={stop.status === "neutral" ? "this-shift-name muted" : "this-shift-name"}>{stop.name}</span>
                  <span className={`this-shift-status ${stop.status}`}>
                    {stop.status === "ok" &&
                      `${stop.lastScanAt ? new Date(stop.lastScanAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" }) : ""} · in range`}
                    {stop.status === "flag" &&
                      `${stop.lastScanAt ? new Date(stop.lastScanAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" }) : ""} · ${stop.distanceMeters ?? "?"} m off`}
                    {stop.status === "danger" && `Missed${stop.dueAt ? ` · was due ${stop.dueAt.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}` : ""}`}
                    {stop.status === "neutral" && (stop.dueAt ? `Due ${stop.dueAt.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}` : "Not yet due")}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
}
