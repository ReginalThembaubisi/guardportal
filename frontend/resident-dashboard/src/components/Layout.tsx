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
          <NavLink to="/invitations/pending" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Pending Invitations
          </NavLink>
          <NavLink to="/vehicles" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            My Vehicles
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
          <NavLink to="/incidents" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Incidents
          </NavLink>
          <NavLink to="/visitor-history" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Visitor History
          </NavLink>
        </nav>
      )}
      {hasRole("CLIENT") && (
        <nav className="top-nav">
          <NavLink to="/residents" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Residents
          </NavLink>
        </nav>
      )}
      {hasRole("SUPERVISOR") && (
        <nav className="top-nav">
          <NavLink to="/incidents" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Incidents
          </NavLink>
          <NavLink to="/visitor-history" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Visitor History
          </NavLink>
        </nav>
      )}
      {hasRole("ADMIN") && (
        <nav className="top-nav">
          <NavLink to="/admin/properties" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Properties
          </NavLink>
          <NavLink to="/admin/units" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Units
          </NavLink>
          <NavLink to="/admin/staff" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Staff
          </NavLink>
          <NavLink to="/admin/link-staff" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Link Staff
          </NavLink>
          <NavLink to="/admin/audit" className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
            Audit
          </NavLink>
        </nav>
      )}

      <main>{children}</main>
    </div>
  );
}
