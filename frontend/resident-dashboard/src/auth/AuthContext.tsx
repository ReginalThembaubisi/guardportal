import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { AuthResponse, Role } from "../api/types";

interface AuthState {
  token: string;
  userId: number;
  fullName: string;
  roles: Role[];
}

interface AuthContextValue {
  auth: AuthState | null;
  login: (response: AuthResponse) => void;
  logout: () => void;
  hasRole: (role: Role) => boolean;
}

const STORAGE_KEY = "psp.auth";

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
        }),
      logout: () => setAuth(null),
      hasRole: (role) => auth?.roles.includes(role) ?? false,
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
