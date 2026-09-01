import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type {
  PropertyManagerResponse,
  PropertyResponse,
  PropertySupervisorResponse,
  ShiftCoverageSlot,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

function isoWeekMonday(d: Date): string {
  const day = d.getDay();
  const diff = (day === 0 ? -6 : 1) - day;
  const monday = new Date(d);
  monday.setDate(d.getDate() + diff);
  return monday.toISOString().slice(0, 10);
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function fmtDate(iso: string): string {
  return new Date(iso + "T00:00:00").toLocaleDateString("en-ZA", {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function fmtTime(isoStr: string): string {
  const d = new Date(isoStr);
  return d.toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", hour12: false });
}

function fmtLocalTime(t: string | null): string {
  if (!t) return "—";
  return t.slice(0, 5);
}

export default function ShiftCoveragePage() {
  const { auth, hasRole } = useAuth();
  const [propertyId, setPropertyId] = useState<number | null>(null);
  const [propertyOptions, setPropertyOptions] = useState<{ id: number; name: string }[] | null>(null);
  const [from, setFrom] = useState(isoWeekMonday(new Date()));
  const [to, setTo] = useState(today());
  const [slots, setSlots] = useState<ShiftCoverageSlot[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    const endpoint = hasRole("ADMIN")
      ? "/api/v1/properties"
      : hasRole("PROPERTY_MANAGER")
      ? "/api/v1/property-managers/mine"
      : "/api/v1/property-supervisors/mine";
    apiFetch<PropertyResponse[] | PropertyManagerResponse[] | PropertySupervisorResponse[]>(endpoint, {
      token: auth.token,
    })
      .then((data) => {
        const mapped = hasRole("ADMIN")
          ? (data as PropertyResponse[]).map((p) => ({ id: p.id, name: p.name }))
          : (data as PropertyManagerResponse[] | PropertySupervisorResponse[]).map((pm) => ({
              id: pm.propertyId,
              name: pm.propertyName,
            }));
        setPropertyOptions(mapped);
        if (mapped.length > 0) setPropertyId(mapped[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load properties"));
  }, [auth, hasRole]);

  useEffect(() => {
    if (!auth || propertyId === null) return;
    setSlots(null);
    apiFetch<ShiftCoverageSlot[]>(
      `/api/v1/shifts/coverage?propertyId=${propertyId}&from=${from}&to=${to}`,
      { token: auth.token }
    )
      .then(setSlots)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load coverage"));
  }, [auth, propertyId, from, to]);

  // Group slots by date for display
  const byDate: Map<string, ShiftCoverageSlot[]> = new Map();
  for (const slot of slots ?? []) {
    const existing = byDate.get(slot.shiftDate) ?? [];
    existing.push(slot);
    byDate.set(slot.shiftDate, existing);
  }

  return (
    <Layout title="Coverage">
      {error && <p className="error">{error}</p>}

      <div className="coverage-filters">
        {propertyOptions && propertyOptions.length > 1 && (
          <label>
            Property
            <select value={propertyId ?? ""} onChange={(e) => setPropertyId(Number(e.target.value))}>
              {propertyOptions.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          From
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          To
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
      </div>

      {propertyOptions && propertyOptions.length === 0 && (
        <p className="empty">You aren't linked to any property yet.</p>
      )}

      {slots && slots.length === 0 && (
        <p className="empty">No rostered shifts in this date range.</p>
      )}

      {slots && slots.length > 0 && (
        <div className="coverage-list">
          {[...byDate.entries()].map(([date, daySlots]) => {
            const worked = daySlots.filter((s) => s.status === "WORKED").length;
            const open = daySlots.filter((s) => s.status === "OPEN").length;
            const noShow = daySlots.filter((s) => s.status === "NO_SHOW").length;
            return (
              <div key={date} className="coverage-day">
                <div className="coverage-day-header">
                  <span className="coverage-day-label">{fmtDate(date)}</span>
                  <span className="coverage-day-summary">
                    {worked > 0 && <span className="cov-count cov-worked">{worked} worked</span>}
                    {open > 0 && <span className="cov-count cov-open">{open} open</span>}
                    {noShow > 0 && <span className="cov-count cov-noshow">{noShow} no-show</span>}
                  </span>
                </div>
                {daySlots.map((slot) => (
                  <div
                    key={slot.scheduleId}
                    className={`coverage-slot coverage-slot-${slot.status.toLowerCase().replace("_", "-")}`}
                  >
                    <div className="coverage-slot-guard">{slot.guardName}</div>
                    <div className="coverage-slot-sched">
                      {slot.shiftType} · {fmtLocalTime(slot.startTime)}–{fmtLocalTime(slot.endTime)}
                    </div>
                    {slot.status === "WORKED" && slot.clockInAt && (
                      <div className="coverage-slot-actual">
                        In {fmtTime(slot.clockInAt)} · Out{" "}
                        {slot.clockOutAt ? fmtTime(slot.clockOutAt) : "—"}
                        {slot.clockOutSource === "ROSTER_AUTO_CLOSED" && (
                          <span className="coverage-slot-badge">roster closed</span>
                        )}
                        {slot.clockOutSource === "CLIENT_CLAIMED_LATE" && (
                          <span className="coverage-slot-badge">end unverified</span>
                        )}
                      </div>
                    )}
                    {slot.status === "OPEN" && slot.clockInAt && (
                      <div className="coverage-slot-actual">In {fmtTime(slot.clockInAt)} · still open</div>
                    )}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      )}

      {slots === null && propertyId !== null && !error && (
        <p className="empty">Loading…</p>
      )}
    </Layout>
  );
}
