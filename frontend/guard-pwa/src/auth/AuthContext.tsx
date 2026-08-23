import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { apiFetch } from "../api/client";
import type { AuthResponse, Role, ShiftResponse } from "../api/types";

interface AuthState {
  token: string;
  userId: number;
  fullName: string;
  roles: Role[];
  /**
   * The guard's own property id. Not returned by the login response (no
   * self-service "my guard profile" endpoint exists), so it's learned the
   * first time a check-in or exit response reveals it, and persisted from
   * there. Can also be set manually — the backend enforces that a guard can
   * only ever act on their own property regardless, so a wrong value here
   * just produces a clear "not yours" error rather than a security gap.
   */
  propertyId: number | null;
  /**
   * The guard's currently open shift, if any — drives whether the Clock
   * page shows "Clock in" or "Clock out". Refreshed from
   * GET /api/v1/shifts/current whenever the token changes (fresh login or
   * restoring a session from storage), so it reflects the server's view
   * rather than possibly-stale local state. Also set directly by clock-in/
   * clock-out responses so the UI updates immediately without waiting on
   * that refetch.
   */
  openShift: ShiftResponse | null;
}

interface AuthContextValue {
  auth: AuthState | null;
  login: (response: AuthResponse) => void;
  logout: () => void;
  hasRole: (role: Role) => boolean;
  setPropertyId: (propertyId: number) => void;
  setOpenShift: (shift: ShiftResponse | null) => void;
}

const STORAGE_KEY = "guard-psp.auth";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function loadFromStorage(): AuthState | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthState;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(() => loadFromStorage());

  useEffect(() => {
    if (auth) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }, [auth]);

  // Authoritative refresh of clock-in state: runs on a fresh login and on
  // restoring a session from storage (both change the token), so the app
  // never shows "not clocked in" just because local state didn't carry
  // over — the actual bug reported ("clock in every time I open the app").
  // Deliberately keyed on the token, not on `auth` as a whole, so this
  // doesn't refire every time clockIn/clockOut update openShift themselves.
  useEffect(() => {
    const token = auth?.token;
    if (!token) return;
    let cancelled = false;
    apiFetch<ShiftResponse | undefined>("/api/v1/shifts/current", { token })
      .then((shift) => {
        if (cancelled) return;
        setAuth((prev) =>
          prev && prev.token === token
            ? { ...prev, openShift: shift ?? null, propertyId: shift?.propertyId ?? prev.propertyId }
            : prev,
        );
      })
      .catch(() => {
        // Not fatal — e.g. offline. Keep whatever was cached locally.
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.token]);

  const value = useMemo<AuthContextValue>(
    () => ({
      auth,
      login: (response) =>
        setAuth({
          token: response.token,
          userId: response.userId,
          fullName: response.fullName,
          roles: response.roles,
          propertyId: null,
          openShift: null,
        }),
      logout: () => setAuth(null),
      hasRole: (role) => auth?.roles.includes(role) ?? false,
      setPropertyId: (propertyId) => setAuth((prev) => (prev ? { ...prev, propertyId } : prev)),
      setOpenShift: (openShift) => setAuth((prev) => (prev ? { ...prev, openShift } : prev)),
    }),
    [auth],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
