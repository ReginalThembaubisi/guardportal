import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { PropertyManagerResponse, PropertyResponse, PropertySupervisorResponse, ShiftSummaryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Seal from "../components/Seal";
import Layout from "../components/Layout";

function fmtDatetime(isoStr: string): string {
  const d = new Date(isoStr);
  return d.toLocaleString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

function ordinalSuffix(n: number): string {
  const mod100 = n % 100;
  if (mod100 >= 11 && mod100 <= 13) return `${n}th`;
  const mod10 = n % 10;
  if (mod10 === 1) return `${n}st`;
  if (mod10 === 2) return `${n}nd`;
  if (mod10 === 3) return `${n}rd`;
  return `${n}th`;
}

export default function ShiftListPage() {
  const { auth, hasRole } = useAuth();
  const [propertyId, setPropertyId] = useState<number | null>(null);
  const [propertyOptions, setPropertyOptions] = useState<{ id: number; name: string }[] | null>(null);
  const [shifts, setShifts] = useState<ShiftSummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    const endpoint = hasRole("ADMIN")
      ? "/api/v1/properties"
      : hasRole("PROPERTY_MANAGER")
      ? "/api/v1/property-managers/mine"
      : "/api/v1/property-supervisors/mine";
    apiFetch<PropertyResponse[] | PropertyManagerResponse[] | PropertySupervisorResponse[]>(endpoint, { token: auth.token })
      .then((data) => {
        const mapped = hasRole("ADMIN")
          ? (data as PropertyResponse[]).map((p) => ({ id: p.id, name: p.name }))
          : (data as PropertyManagerResponse[] | PropertySupervisorResponse[]).map((pm) => ({ id: pm.propertyId, name: pm.propertyName }));
        setPropertyOptions(mapped);
        if (mapped.length > 0) setPropertyId(mapped[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load properties"));
  }, [auth, hasRole]);

  useEffect(() => {
    if (!auth || propertyId === null) return;
    setShifts(null);
    apiFetch<ShiftSummaryResponse[]>(`/api/v1/shifts?propertyId=${propertyId}`, { token: auth.token })
      .then(setShifts)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load shifts"));
  }, [auth, propertyId]);

  return (
    <Layout title="Shifts">
      {error && <p className="error">{error}</p>}

      {propertyOptions && propertyOptions.length === 0 && (
        <p className="empty">You aren't linked to any property yet.</p>
      )}

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

      {propertyOptions && propertyOptions.length > 0 && (
        <>
          <h2>Recent shifts ({shifts?.length ?? "…"})</h2>

          {shifts && shifts.length === 0 && (
            <p className="empty">No shifts recorded for this property yet.</p>
          )}

          {shifts && shifts.length > 0 && (
            <div className="shift-list">
              {shifts.map((s) => {
                const isOpen = s.clockOutAt === null;
                const isAutoClose = s.clockOutSource === "ROSTER_AUTO_CLOSED";
                const isLate = s.clockOutSource === "CLIENT_CLAIMED_LATE";

                const rowClass = [
                  "shift-row",
                  isOpen ? "shift-open" : "",
                  isAutoClose ? "shift-auto-closed" : "",
                  isLate ? "shift-unverified" : "",
                ]
                  .filter(Boolean)
                  .join(" ");

                return (
                  <div key={s.id} className={rowClass}>
                    <div className="shift-row-body">
                      <div className="shift-row-guard">{s.guardName}</div>

                      <div className="shift-row-time">
                        In: {fmtDatetime(s.clockInAt)}
                        {" · "}
                        {isOpen ? (
                          <span className="shift-row-open-label">still open</span>
                        ) : (
                          <>Out: {fmtDatetime(s.clockOutAt!)}</>
                        )}
                      </div>

                      {isAutoClose && (
                        <div className="shift-row-note">closed by roster, guard did not clock out</div>
                      )}
                      {isLate && (
                        <div className="shift-row-note">end time unverified — submitted late</div>
                      )}
                      {isAutoClose && s.weekAutoCloseOrdinal > 1 && (
                        <div className="shift-row-ordinal">
                          {ordinalSuffix(s.weekAutoCloseOrdinal)} auto-close this week
                        </div>
                      )}
                    </div>

                    <div className="shift-row-seal">
                      {isAutoClose && <Seal state="late">roster closed</Seal>}
                      {isLate && <Seal state="late">end unverified</Seal>}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}
    </Layout>
  );
}
