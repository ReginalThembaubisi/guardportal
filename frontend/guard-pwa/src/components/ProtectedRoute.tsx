import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { auth } = useAuth();

  if (!auth) {
    return <Navigate to="/" replace />;
  }
  if (!auth.roles.includes("GUARD")) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
