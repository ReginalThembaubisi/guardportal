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
          {auth && <span className="user-name">{auth.fullName}</span>}
          <button className="link-button" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </header>
      <main>{children}</main>
    </div>
  );
}
