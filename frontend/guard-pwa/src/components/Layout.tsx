import { useState, type ReactNode } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

const MORE_ROUTES = ["/walk-in", "/vehicle-history", "/report-incident", "/shifts"];

export default function Layout({ title, children }: { title: string; children: ReactNode }) {
  const { auth, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);
  const onMoreRoute = MORE_ROUTES.includes(location.pathname);

  function handleLogout() {
    logout();
    navigate("/");
  }

  function goTo(path: string) {
    setMoreOpen(false);
    navigate(path);
  }

  return (
    <div className="layout">
      <header className="layout-header">
        <h1>{title}</h1>
        <div className="layout-header-right">
          {auth && (
            <span className="user-name">
              {auth.fullName}
              {/* Only known while actually on shift — not shown when off duty, since a
                  last-known property could mislead after a reassignment. */}
              {auth.openShift && <span className="user-property"> · {auth.openShift.propertyName}</span>}
            </span>
          )}
          <button className="link-button" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </header>
      <main>{children}</main>

      {moreOpen && (
        <>
          <div className="nav-sheet-backdrop" onClick={() => setMoreOpen(false)} />
          <div className="nav-sheet">
            <button type="button" className="nav-sheet-item" onClick={() => goTo("/walk-in")}>
              Walk-in
            </button>
            <button type="button" className="nav-sheet-item" onClick={() => goTo("/vehicle-history")}>
              Vehicles
            </button>
            <button type="button" className="nav-sheet-item" onClick={() => goTo("/report-incident")}>
              Incident
            </button>
            <button type="button" className="nav-sheet-item" onClick={() => goTo("/shifts")}>
              My Shifts
            </button>
            <button type="button" className="nav-sheet-cancel" onClick={() => setMoreOpen(false)}>
              Cancel
            </button>
          </div>
        </>
      )}

      <nav className="bottom-nav">
        <NavLink to="/clock" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Clock
        </NavLink>
        <NavLink to="/checkin" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Check in
        </NavLink>
        <NavLink to="/scan" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Checkpoint
        </NavLink>
        <NavLink to="/occupancy" className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}>
          Occupancy
        </NavLink>
        <button
          type="button"
          className={onMoreRoute ? "nav-item active" : "nav-item"}
          onClick={() => setMoreOpen(true)}
        >
          More
        </button>
      </nav>
    </div>
  );
}
