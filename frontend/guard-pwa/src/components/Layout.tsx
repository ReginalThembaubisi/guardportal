import { useState, type ReactNode } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/**
 * Everything that isn't one of the three most-reached-for actions (Check in,
 * Check out, Checkpoint — all done many times a shift). Clock lives here
 * too now: it only happens twice a shift, so it doesn't earn one of the
 * three precious always-visible slots even though it's important. On phone
 * width these live behind the hamburger drawer so the bottom bar stays down
 * to a true minimum instead of a row of five-plus cramped tabs. On wider
 * screens there's no thumb-zone constraint and plenty of room, so the same
 * items render directly in the sidebar (see the
 * `nav-item-secondary`/`nav-item-menu` display rules in index.css) and the
 * hamburger never appears.
 */
const SECONDARY_ITEMS: { path: string; label: string }[] = [
  { path: "/clock", label: "Clock" },
  { path: "/walk-in", label: "Walk-in" },
  { path: "/vehicle-history", label: "Vehicle History" },
  { path: "/report-incident", label: "Report Incident" },
  { path: "/shifts", label: "My Shifts" },
];
const SECONDARY_ROUTES = SECONDARY_ITEMS.map((item) => item.path);

export default function Layout({ title, children }: { title: string; children: ReactNode }) {
  const { auth, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const onSecondaryRoute = SECONDARY_ROUTES.includes(location.pathname);

  function handleLogout() {
    logout();
    navigate("/");
  }

  function goTo(path: string) {
    setMenuOpen(false);
    navigate(path);
  }

  function navLinkClass({ isActive }: { isActive: boolean }) {
    return isActive ? "nav-item active" : "nav-item";
  }

  function secondaryNavLinkClass({ isActive }: { isActive: boolean }) {
    return isActive ? "nav-item nav-item-secondary active" : "nav-item nav-item-secondary";
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

      {/* Only ever opened from the hamburger tile, which CSS hides above
          phone width — so this never opens on a screen where the sidebar
          already lists everything directly. */}
      {menuOpen && (
        <>
          <div className="nav-drawer-backdrop" onClick={() => setMenuOpen(false)} />
          <div className="nav-drawer">
            <div className="nav-drawer-header">
              <span>Menu</span>
              <button type="button" className="nav-drawer-close" onClick={() => setMenuOpen(false)} aria-label="Close menu">
                ×
              </button>
            </div>
            {SECONDARY_ITEMS.map((item) => (
              <button key={item.path} type="button" className="nav-drawer-item" onClick={() => goTo(item.path)}>
                {item.label}
              </button>
            ))}
          </div>
        </>
      )}

      <nav className="bottom-nav">
        <NavLink to="/checkin" className={navLinkClass}>
          Check in
        </NavLink>
        <NavLink to="/occupancy" className={navLinkClass}>
          Check out
        </NavLink>
        <NavLink to="/scan" className={navLinkClass}>
          Checkpoint
        </NavLink>
        {SECONDARY_ITEMS.map((item) => (
          <NavLink key={item.path} to={item.path} className={secondaryNavLinkClass}>
            {item.label}
          </NavLink>
        ))}
        <button
          type="button"
          className={onSecondaryRoute ? "nav-item nav-item-menu active" : "nav-item nav-item-menu"}
          onClick={() => setMenuOpen(true)}
          aria-label="More"
        >
          <span className="hamburger-icon" aria-hidden="true">
            <span />
            <span />
            <span />
          </span>
        </button>
      </nav>
    </div>
  );
}
