import type { ReactNode } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function Layout({ title, children }: { title: string; children: ReactNode }) {
  const { auth, logout, hasRole } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/");
  }

  return (
    <div className="layout">
      <header className="layout-header">
        <h1>{title}</h1>
        <div className="layout-header-right">
          {auth && <span className="user-name">{auth.fullName}</span>}
          <button className="link-button" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </header>

      {hasRole("RESIDENT") && (
        <nav className="top-nav">
          <NavLink to="/history" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            History
          </NavLink>
          <NavLink to="/invite" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            New Invitation
          </NavLink>
        </nav>
      )}
      {hasRole("PROPERTY_MANAGER") && (
        <nav className="top-nav">
          <NavLink to="/dashboard" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Dashboard
          </NavLink>
          <NavLink to="/residents/new" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Add Resident
          </NavLink>
          <NavLink to="/guards/new" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Add Guard
          </NavLink>
          <NavLink to="/checkpoints/new" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Add Checkpoint
          </NavLink>
          <NavLink to="/patrol-routes/new" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Add Patrol Route
          </NavLink>
          <NavLink to="/patrol-status" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Patrol Status
          </NavLink>
          <NavLink to="/vehicle-history" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Vehicle History
          </NavLink>
        </nav>
      )}

      <main>{children}</main>
    </div>
  );
}
