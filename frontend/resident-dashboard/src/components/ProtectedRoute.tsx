import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import type { Role } from "../api/types";
import { useAuth } from "../auth/AuthContext";

export default function ProtectedRoute({ role, children }: { role: Role | Role[]; children: ReactNode }) {
  const { auth } = useAuth();

  if (!auth) {
    return <Navigate to="/" replace />;
  }
  const allowedRoles = Array.isArray(role) ? role : [role];
  if (!allowedRoles.some((r) => auth.roles.includes(r))) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
