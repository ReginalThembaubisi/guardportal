import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import type { Role } from "../api/types";
import { useAuth } from "../auth/AuthContext";

export default function ProtectedRoute({ role, children }: { role: Role; children: ReactNode }) {
  const { auth } = useAuth();

  if (!auth) {
    return <Navigate to="/" replace />;
  }
  if (!auth.roles.includes(role)) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
