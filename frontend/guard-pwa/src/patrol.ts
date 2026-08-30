import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "./api/client";
import type { MissedCheckpointResponse, PatrolRouteResponse, ShiftScheduleResponse } from "./api/types";
import { useAuth } from "./auth/AuthContext";

/** Formats a JS Date as a LocalDateTime param (`yyyy-MM-ddTHH:mm:ss`, no zone) — matches what Spring's `@DateTimeFormat(iso = DATE_TIME)` expects, and stays in wall-clock time rather than shifting to UTC like `toISOString()` would. */
export function toLocalDateTimeParam(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export interface PatrolStop {
  checkpointId: number;
  name: string;
  sequenceOrder: number;
  scanned: boolean;
  scanCount: number;
  lastScanAt: string | null;
  distanceMeters: number | null;
  withinTolerance: boolean | null;
  /** Client-side heuristic only — no due-time concept exists server-side. Null when today's shift schedule isn't known. */
  dueAt: Date | null;
  status: "ok" | "flag" | "danger" | "neutral";
}

/**
 * No due-time field exists anywhere in the schema (checkpoints/routes carry
 * no timing data), so "due at" is approximated by evenly dividing the
 * scheduled shift window across the route's stops in sequence order.
 * Handles a shift crossing midnight (e.g. NIGHT 18:00-06:00).
 */
function computeDueTimes(shiftDate: string, startTime: string, endTime: string, stopCount: number): Date[] {
  const start = new Date(`${shiftDate}T${startTime}`);
  let end = new Date(`${shiftDate}T${endTime}`);
  if (end.getTime() <= start.getTime()) {
    end = new Date(end.getTime() + 24 * 60 * 60 * 1000);
  }
  const totalMs = end.getTime() - start.getTime();
  const dues: Date[] = [];
  for (let i = 1; i <= stopCount; i++) {
    dues.push(new Date(start.getTime() + (totalMs * i) / stopCount));
  }
  return dues;
}

export interface PatrolStatus {
  routeId: number | null;
  routeName: string | null;
  stops: PatrolStop[];
  scannedCount: number;
  totalCount: number;
  missedCount: number;
  nextUp: PatrolStop | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

/**
 * Shared by HomePage (tiles) and PatrolPage (full screen) so the
 * route-discovery + status-fetch + due-time heuristic lives in one place.
 * "Next up" = earliest sequence-order stop with no scan this shift,
 * regardless of due time — a due-time comparison only affects display
 * (missed vs. not-yet-due), never which stop is offered next.
 */
export function usePatrolStatus(todayShift: ShiftScheduleResponse | null): PatrolStatus {
  const { auth } = useAuth();
  const [routeId, setRouteId] = useState<number | null>(null);
  const [routeName, setRouteName] = useState<string | null>(null);
  const [checkpoints, setCheckpoints] = useState<MissedCheckpointResponse["checkpoints"] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    if (!auth || auth.propertyId === null || !auth.openShift) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    const clockInAt = auth.openShift.clockInAt;
    apiFetch<PatrolRouteResponse[]>(`/api/v1/patrol-routes?propertyId=${auth.propertyId}`, { token: auth.token })
      .then((routes) => {
        const first = routes[0] ?? null;
        if (!first) {
          setRouteId(null);
          setRouteName(null);
          setCheckpoints([]);
          setLoading(false);
          return;
        }
        setRouteId(first.id);
        setRouteName(first.name);
        const to = toLocalDateTimeParam(new Date());
        return apiFetch<MissedCheckpointResponse>(
          `/api/v1/patrol-routes/${first.id}/checkpoint-status?from=${encodeURIComponent(clockInAt)}&to=${encodeURIComponent(to)}`,
          { token: auth.token },
        ).then((status) => {
          setCheckpoints(status.checkpoints);
          setLoading(false);
        });
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "Failed to load patrol status");
        setLoading(false);
      });
  }, [auth]);

  useEffect(refresh, [refresh]);

  const now = new Date();
  const dueTimes =
    todayShift?.startTime && todayShift?.endTime
      ? computeDueTimes(todayShift.shiftDate, todayShift.startTime, todayShift.endTime, checkpoints?.length ?? 0)
      : null;

  const stops: PatrolStop[] = (checkpoints ?? [])
    .slice()
    .sort((a, b) => a.sequenceOrder - b.sequenceOrder)
    .map((cp, index) => {
      const dueAt = dueTimes ? dueTimes[index] : null;
      let status: PatrolStop["status"];
      if (cp.scanned) {
        status = cp.withinTolerance === false ? "flag" : "ok";
      } else if (dueAt && dueAt.getTime() < now.getTime()) {
        status = "danger";
      } else {
        status = "neutral";
      }
      return {
        checkpointId: cp.checkpointId,
        name: cp.name,
        sequenceOrder: cp.sequenceOrder,
        scanned: cp.scanned,
        scanCount: cp.scanCount,
        lastScanAt: cp.lastScanAt,
        distanceMeters: cp.distanceMeters,
        withinTolerance: cp.withinTolerance,
        dueAt,
        status,
      };
    });

  return {
    routeId,
    routeName,
    stops,
    scannedCount: stops.filter((s) => s.scanned).length,
    totalCount: stops.length,
    missedCount: stops.filter((s) => s.status === "danger").length,
    nextUp: stops.find((s) => !s.scanned) ?? null,
    loading,
    error,
    refresh,
  };
}
