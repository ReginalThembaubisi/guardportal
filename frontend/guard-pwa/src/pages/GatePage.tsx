import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type {
  OccupancyResponse,
  VisitorCategory,
  VisitorCheckInResponse,
  VisitorCheckOutResponse,
  VisitorEntryResponse,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import QrScanner from "../components/QrScanner";
import Seal from "../components/Seal";
import { VehicleHistoryContent } from "./VehicleHistoryPage";

type Segment = "checkin" | "onsite" | "vehicles";
const CATEGORIES: VisitorCategory[] = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF"];

/**
 * Everything about people at the boundary, in one place — resolves the old
 * naming problem where the bottom nav read "Check in" / "Check out" /
 * "Checkpoint": a pair that looked like opposites but wasn't (the old
 * "Check out" tab opened Occupancy), plus a third repeating "check in" with
 * an unrelated meaning. Segment is local state, not routed — flipping
 * between "who's here" and "check someone in" shouldn't build history
 * entries.
 */
export default function GatePage() {
  const { auth, setPropertyId } = useAuth();
  const [searchParams] = useSearchParams();
  const initialSegment = (searchParams.get("segment") as Segment | null) ?? "checkin";
  const [segment, setSegment] = useState<Segment>(initialSegment);

  const [occupancy, setOccupancy] = useState<OccupancyResponse | null>(null);
  const [occupancyError, setOccupancyError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [exitingId, setExitingId] = useState<number | null>(null);
  const [lastCheckOut, setLastCheckOut] = useState<VisitorCheckOutResponse | null>(null);

  const [vehicleRegistration, setVehicleRegistration] = useState("");
  const [qrToken, setQrToken] = useState("");
  const [checkInError, setCheckInError] = useState<string | null>(null);
  const [checkInBusy, setCheckInBusy] = useState(false);
  const [lastCheckIn, setLastCheckIn] = useState<VisitorCheckInResponse | null>(null);

  const loadOccupancy = useCallback(() => {
    if (!auth || auth.propertyId === null) return;
    setOccupancyError(null);
    apiFetch<OccupancyResponse>(`/api/v1/properties/${auth.propertyId}/occupancy`, { token: auth.token })
      .then(setOccupancy)
      .catch((err) => setOccupancyError(err instanceof ApiError ? err.message : "Failed to load occupancy"));
  }, [auth]);

  useEffect(loadOccupancy, [loadOccupancy]);

  async function submitCheckIn(token: string) {
    if (!auth || checkInBusy) return;
    setCheckInError(null);
    setCheckInBusy(true);
    try {
      const entry = await apiFetch<VisitorCheckInResponse>("/api/v1/visitor-entries", {
        method: "POST",
        token: auth.token,
        body: { qrToken: token.trim(), vehicleRegistration: vehicleRegistration.trim() || undefined },
      });
      setLastCheckIn(entry);
      setPropertyId(entry.propertyId);
      setQrToken("");
      loadOccupancy();
    } catch (err) {
      setCheckInError(err instanceof ApiError ? err.message : "Check-in failed");
    } finally {
      setCheckInBusy(false);
    }
  }

  function handleManualCheckIn(e: FormEvent) {
    e.preventDefault();
    submitCheckIn(qrToken);
  }

  async function handleExit(entryId: number) {
    if (!auth) return;
    setOccupancyError(null);
    setExitingId(entryId);
    try {
      const result = await apiFetch<VisitorCheckOutResponse>(`/api/v1/visitor-entries/${entryId}/exit`, {
        method: "POST",
        token: auth.token,
      });
      setLastCheckOut(result);
      loadOccupancy();
    } catch (err) {
      setOccupancyError(err instanceof ApiError ? err.message : "Failed to check visitor out");
    } finally {
      setExitingId(null);
    }
  }

  const trimmedQuery = searchQuery.trim().toLowerCase();
  const searchResults: VisitorEntryResponse[] = trimmedQuery
    ? CATEGORIES.flatMap((category) => occupancy?.byCategory[category] ?? []).filter(
        (entry) =>
          entry.visitorName.toLowerCase().includes(trimmedQuery) ||
          (entry.vehicleRegistration ?? "").toLowerCase().includes(trimmedQuery),
      )
    : [];

  function entryRow(entry: VisitorEntryResponse) {
    return (
      <li key={entry.id}>
        <div className="entry-row">
          <div>
            <strong>{entry.visitorName}</strong>
            <span className="entry-meta">
              {entry.vehicleRegistration && (
                <>
                  {" "}
                  · {entry.vehicleRegistration}
                  {entry.vehicleRecognized && <span className="badge recognized"> recognized</span>}
                </>
              )}
              {" · entered "}
              {new Date(entry.enteredAt).toLocaleTimeString()}
            </span>
          </div>
          <button className="exit-button" onClick={() => handleExit(entry.id)} disabled={exitingId === entry.id}>
            {exitingId === entry.id ? "Checking out…" : "Check out"}
          </button>
        </div>
      </li>
    );
  }

  return (
    <Layout title="Gate">
      <div className="screen-header">
        <div className="screen-header-row">
          <h1 className="screen-title" style={{ fontSize: 23 }}>
            Gate
          </h1>
          <span className="screen-subtitle">{auth?.openShift?.propertyName ?? ""}</span>
        </div>
        <div className="segmented-control cols-3">
          <button type="button" className={segment === "checkin" ? "active" : ""} onClick={() => setSegment("checkin")}>
            Check in
          </button>
          <button type="button" className={segment === "onsite" ? "active" : ""} onClick={() => setSegment("onsite")}>
            On site {occupancy?.totalOnSite ?? ""}
          </button>
          <button type="button" className={segment === "vehicles" ? "active" : ""} onClick={() => setSegment("vehicles")}>
            Vehicles
          </button>
        </div>
      </div>

      <div className="screen-content tight">
        {segment === "checkin" && (
          <>
            {checkInError && <p className="error">{checkInError}</p>}

            <div className="camera-viewport">
              <span className="camera-corner tl" aria-hidden="true" />
              <span className="camera-corner tr" aria-hidden="true" />
              <span className="camera-corner bl" aria-hidden="true" />
              <span className="camera-corner br" aria-hidden="true" />
              <QrScanner onDecode={submitCheckIn} />
              <span className="camera-hint">Point at the visitor's QR</span>
            </div>

            <label>
              Vehicle registration — before you scan, if there is one
              <input
                type="text"
                className="guard-input"
                value={vehicleRegistration}
                onChange={(e) => setVehicleRegistration(e.target.value.toUpperCase())}
                placeholder="CA 123 456"
              />
            </label>

            <div className="fallback-divider">
              <span className="fallback-divider-rule" />
              <span className="fallback-divider-text">Camera not working?</span>
              <span className="fallback-divider-rule" />
            </div>

            <form onSubmit={handleManualCheckIn} className="manual-entry-row">
              <input
                type="text"
                className="guard-input"
                value={qrToken}
                onChange={(e) => setQrToken(e.target.value)}
                placeholder="Type the code"
                required
              />
              <button type="submit" disabled={checkInBusy}>
                {checkInBusy ? "…" : "Check in"}
              </button>
            </form>

            {lastCheckIn && (
              <div className="checkin-result">
                <h2>Checked in</h2>
                <p className="checkin-visitor-name">{lastCheckIn.visitorName}</p>
                {lastCheckIn.visitingResidentName && (
                  <p className="checkin-visiting">Visiting {lastCheckIn.visitingResidentName}</p>
                )}
                <p className="entry-meta">
                  {lastCheckIn.category}
                  {lastCheckIn.vehicleRegistration && ` · ${lastCheckIn.vehicleRegistration}`}
                  {" · "}
                  {new Date(lastCheckIn.enteredAt).toLocaleTimeString()}
                  {lastCheckIn.vehicleRecognized && (
                    <>
                      {" "}
                      <Seal state="cleared">Recognised</Seal>
                    </>
                  )}
                </p>
              </div>
            )}

            <Link to="/walk-in" className="walk-in-escape" style={{ display: "block", textAlign: "center", textDecoration: "none" }}>
              No invitation? Log a walk-in
            </Link>
          </>
        )}

        {segment === "onsite" && (
          <>
            {occupancyError && <p className="error">{occupancyError}</p>}

            {!auth || auth.propertyId === null ? (
              <p className="empty">Your property hasn't been detected yet — it's picked up automatically after your first check-in.</p>
            ) : (
              <>
                <div className="occupancy-summary" style={{ marginBottom: 0 }}>
                  <button className="refresh-button" onClick={loadOccupancy}>
                    Refresh
                  </button>
                </div>

                {lastCheckOut && (
                  <div className="checkin-result">
                    <h2>Checked out</h2>
                    <p className="checkin-visitor-name">{lastCheckOut.visitorName}</p>
                    <p className="entry-meta">
                      In {new Date(lastCheckOut.enteredAt).toLocaleTimeString()} · out{" "}
                      {new Date(lastCheckOut.exitedAt).toLocaleTimeString()}
                    </p>
                  </div>
                )}

                <input
                  type="search"
                  className="sticky-search"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search by name or vehicle reg to check out fast"
                  autoFocus
                />

                {occupancy && trimmedQuery && (
                  <section className="occupancy-category">
                    {searchResults.length === 0 ? (
                      <p className="empty">No one on site matches "{searchQuery.trim()}".</p>
                    ) : (
                      <ul>{searchResults.map(entryRow)}</ul>
                    )}
                  </section>
                )}

                {occupancy && !trimmedQuery && (
                  <div className="occupancy-grid">
                    {CATEGORIES.map((category) => {
                      const entries = occupancy.byCategory[category] ?? [];
                      return (
                        <section key={category} className="occupancy-category">
                          <h2>
                            {category} <span className="count-badge">{entries.length}</span>
                          </h2>
                          {entries.length === 0 ? <p className="empty">None</p> : <ul>{entries.map(entryRow)}</ul>}
                        </section>
                      );
                    })}
                  </div>
                )}
              </>
            )}
          </>
        )}

        {segment === "vehicles" && <VehicleHistoryContent />}
      </div>
    </Layout>
  );
}
