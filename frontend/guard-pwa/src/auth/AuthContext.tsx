import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { AuthResponse, Role } from "../api/types";

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
}

interface AuthContextValue {
  auth: AuthState | null;
  login: (response: AuthResponse) => void;
  logout: () => void;
  hasRole: (role: Role) => boolean;
  setPropertyId: (propertyId: number) => void;
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
        }),
      logout: () => setAuth(null),
      hasRole: (role) => auth?.roles.includes(role) ?? false,
      setPropertyId: (propertyId) => setAuth((prev) => (prev ? { ...prev, propertyId } : prev)),
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
