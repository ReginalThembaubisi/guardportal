import type { ReactNode } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/**
 * Five permanent destinations — places, not actions. No hamburger, no
 * drawer: everything a guard needs lives inside one of these five, and
 * availability (e.g. off-duty) is communicated by an in-screen flag strip
 * (see HomePage/PatrolPage), never by dimming a tab label — dimmed labels
 * measured under the contrast floor this surface is held to.
 */
export default function Layout({ title, children }: { title: string; children: ReactNode }) {
  const { auth, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/");
  }

  function navLinkClass({ isActive }: { isActive: boolean }) {
    return isActive ? "nav-item active" : "nav-item";
  }

  return (
    <div className="layout">
      <header className="layout-header">
        <h1>{title}</h1>
        <div className="layout-header-right">
          {auth && (
            <span className="user-name">
              {auth.fullName}
              {auth.openShift && <span className="user-property"> · {auth.openShift.propertyName}</span>}
            </span>
          )}
          <button className="link-button" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </header>
      <main>{children}</main>

      <nav className="bottom-nav">
        <NavLink to="/home" className={navLinkClass}>
          Home
        </NavLink>
        <NavLink to="/gate" className={navLinkClass}>
          Gate
        </NavLink>
        <NavLink to="/patrol" className={navLinkClass}>
          Patrol
        </NavLink>
        <NavLink to="/report" className={navLinkClass}>
          Report
        </NavLink>
        <NavLink to="/me" className={navLinkClass}>
          Me
        </NavLink>
      </nav>
    </div>
  );
}
