import type { ReactNode } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function Layout({ title, children }: { title: string; children: ReactNode }) {
  const { auth, logout } = useAuth();
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
      <main>{children}</main>
      <nav className="bottom-nav">
        <NavLink to="/clock" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Clock
        </NavLink>
        <NavLink to="/checkin" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Check in
        </NavLink>
        <NavLink to="/walk-in" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Walk-in
        </NavLink>
        <NavLink to="/scan" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Scan
        </NavLink>
        <NavLink to="/occupancy" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Occupancy
        </NavLink>
        <NavLink to="/vehicle-history" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Vehicles
        </NavLink>
      </nav>
    </div>
  );
}
