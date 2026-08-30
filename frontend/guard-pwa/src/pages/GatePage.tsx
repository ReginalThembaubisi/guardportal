import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type {
  OccupancyResponse,
  VisitorCategory,
  VisitorCheckInResponse,
  VisitorCheckOutResponse,
  VisitorEntryResponse,
  VisitorHistoryEntryResponse,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import QrScanner from "../components/QrScanner";
import Seal from "../components/Seal";
import { VehicleHistoryContent } from "./VehicleHistoryPage";

type Segment = "checkin" | "onsite" | "vehicles";
const CATEGORIES: VisitorCategory[] = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF"];

const CODE_LENGTH = 6;

/**
 * Live: the backend's `short_code` column/lookup (V13__add_invitation_short_code.sql,
 * CheckInRejectedException) and the resident share screen that sends it
 * both ship together — a code guards can type but residents can't send was
 * half a feature. A typed 6-digit code now goes out as `shortCode`; a
 * scanned UUID always goes out as `qrToken` regardless of this flag.
 */
const USE_SHORT_CODE_FIELD = true;

type CodeOutcomeKind = "expired" | "used" | "notfound";
type CodeOutcome = { kind: CodeOutcomeKind; heading: string; detail: string };

/**
 * A rejected code gets one of three named answers, because each has a
 * different next action — expired and already-used both route to walk-in,
 * a wrong code routes back to the keypad. Collapsing them into one "invalid
 * code" message costs the guard the one piece of information they need.
 *
 * Classifies on `ApiError.reason`, the stable machine-readable field the
 * backend's CheckInRejectedException now sets (EXPIRED / NOT_YET_VALID /
 * ALREADY_USED / NOT_FOUND) — not on the human-readable message, which can
 * be reworded without notice. A missing/unrecognized reason (e.g. before
 * USE_SHORT_CODE_FIELD is flipped, when a typed code is sent as qrToken and
 * rejected by the older, unclassified path) falls back to "not found",
 * matching that path's honest "doesn't match" behaviour.
 *
 * Note what is deliberately NOT distinguished: "no such code at this
 * property" and "no such code anywhere" are the same answer (the backend
 * only ever looks at the guard's own property), and nothing ever indicates
 * which digit was wrong. Otherwise the error text becomes an enumeration
 * oracle for a six-digit space.
 */
function classifyCodeError(err: unknown, code: string): CodeOutcome {
  const reason = err instanceof ApiError ? err.reason : undefined;
  const raw = err instanceof ApiError ? err.message : "Check-in failed";
  const spaced = formatCode(code);

  switch (reason) {
    case "EXPIRED":
    case "NOT_YET_VALID":
      return { kind: "expired", heading: "That code expired", detail: raw };
    case "ALREADY_USED":
      return { kind: "used", heading: "That code was already used", detail: raw };
    case "NOT_FOUND":
    default:
      return {
        kind: "notfound",
        heading: "No invitation with that code",
        detail: `Nothing at this property matches ${spaced} right now. Check it with the visitor, or log a walk-in.`,
      };
  }
}

/** 417302 -> "417 302". The triple grouping is how the code is spoken. */
function formatCode(code: string): string {
  return code.length > 3 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;
}

/**
 * Everything about people at the boundary, in one place — resolves the old
 * naming problem where the bottom nav read "Check in" / "Check out" /
 * "Checkpoint": a pair that looked like opposites but wasn't (the old
 * "Check out" tab opened Occupancy), plus a third repeating "check in" with
 * an unrelated meaning. Segment is local state, not routed — flipping
 * between "who's here" and "check someone in" shouldn't build history
 * entries.
 *
 * Check-in is code-first. `invitation.qrToken` is a 36-character UUID, so the
 * old "type the code manually" fallback was a fallback nobody could use: no
 * guard types 36 hex characters at a gate, at night, one-handed. Camera
 * access on a BYOD phone fails for reasons the product cannot control
 * (permission denied, cracked lens, glare, a visitor's dead screen), and each
 * of those used to end the check-in. So typing a 6-digit code is the primary
 * path and scanning is the always-available secondary.
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
  const [checkedOutToday, setCheckedOutToday] = useState<VisitorHistoryEntryResponse[] | null>(null);

  const [vehicleRegistration, setVehicleRegistration] = useState("");
  const [digits, setDigits] = useState("");
  const [outcome, setOutcome] = useState<CodeOutcome | null>(null);
  const [checkInError, setCheckInError] = useState<string | null>(null);
  const [checkInBusy, setCheckInBusy] = useState(false);
  const [lastCheckIn, setLastCheckIn] = useState<VisitorCheckInResponse | null>(null);
  const [scanning, setScanning] = useState(false);

  const loadOccupancy = useCallback(() => {
    if (!auth || auth.propertyId === null) return;
    setOccupancyError(null);
    apiFetch<OccupancyResponse>(`/api/v1/properties/${auth.propertyId}/occupancy`, { token: auth.token })
      .then(setOccupancy)
      .catch((err) => setOccupancyError(err instanceof ApiError ? err.message : "Failed to load occupancy"));
  }, [auth]);

  useEffect(loadOccupancy, [loadOccupancy]);

  /**
   * Once someone's checked out they drop off the occupancy list entirely
   * (correctly — that list is current-on-site only), which left guards
   * with no way to confirm "did so-and-so already leave today." The
   * backend enforces today-only/own-property regardless of what's asked
   * for here, so this can't be widened into a real history browser just
   * by editing the frontend.
   */
  const loadTodayHistory = useCallback(() => {
    if (!auth || auth.propertyId === null) return;
    const today = new Date().toLocaleDateString("en-CA");
    apiFetch<VisitorHistoryEntryResponse[]>(
      `/api/v1/properties/${auth.propertyId}/visitor-entries/history?from=${today}&to=${today}`,
      { token: auth.token },
    )
      .then((entries) => setCheckedOutToday(entries.filter((e) => e.exitedAt !== null)))
      .catch(() => {
        // Non-fatal — the "checked out today" section just won't show.
      });
  }, [auth]);

  useEffect(loadTodayHistory, [loadTodayHistory]);

  /** Shared by the keypad and the scanner. `code` is digits-only or a UUID. */
  async function submitCheckIn(value: string, viaShortCode: boolean) {
    if (!auth || checkInBusy) return;
    const trimmed = value.trim();
    if (!trimmed) return;

    setCheckInError(null);
    setOutcome(null);
    setCheckInBusy(true);
    try {
      const body =
        viaShortCode && USE_SHORT_CODE_FIELD
          ? { shortCode: trimmed, vehicleRegistration: vehicleRegistration.trim() || undefined }
          : { qrToken: trimmed, vehicleRegistration: vehicleRegistration.trim() || undefined };

      const entry = await apiFetch<VisitorCheckInResponse>("/api/v1/visitor-entries", {
        method: "POST",
        token: auth.token,
        body,
      });
      setLastCheckIn(entry);
      setPropertyId(entry.propertyId);
      setDigits("");
      setVehicleRegistration("");
      setScanning(false);
      loadOccupancy();
    } catch (err) {
      if (viaShortCode) {
        // Keep the digits on screen so the guard can re-read them against
        // what the visitor is holding, rather than having the code cleared
        // out from under them.
        setOutcome(classifyCodeError(err, trimmed));
      } else {
        setCheckInError(err instanceof ApiError ? err.message : "Check-in failed");
      }
    } finally {
      setCheckInBusy(false);
    }
  }

  function handleCodeSubmit(e: FormEvent) {
    e.preventDefault();
    if (digits.length !== CODE_LENGTH) return;
    submitCheckIn(digits, true);
  }

  function pressDigit(d: string) {
    setOutcome(null);
    setDigits((prev) => (prev.length >= CODE_LENGTH ? prev : prev + d));
  }

  function pressBack() {
    setOutcome(null);
    setDigits((prev) => prev.slice(0, -1));
  }

  function pressClear() {
    setOutcome(null);
    setDigits("");
  }

  function nextCode() {
    setLastCheckIn(null);
    setOutcome(null);
    setDigits("");
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
      loadTodayHistory();
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

  const remaining = CODE_LENGTH - digits.length;
  const codeReady = remaining === 0;
  const walkInHref = `/walk-in${
    vehicleRegistration.trim() || digits ? `?reg=${encodeURIComponent(vehicleRegistration.trim())}&code=${digits}` : ""
  }`;

  function codeSlots() {
    return (
      <div className="code-slots" aria-hidden="true">
        {Array.from({ length: CODE_LENGTH }).map((_, i) => {
          const filled = i < digits.length;
          const isNext = !outcome && i === digits.length;
          const classes = ["code-slot"];
          if (outcome) classes.push(filled ? `rejected-${outcome.kind === "notfound" ? "danger" : "flag"}` : "");
          else if (filled) classes.push("filled");
          else if (isNext) classes.push("next");

          return (
            <span key={i} className="code-slot-wrap">
              {i === 3 && <span className="code-slot-separator" />}
              <span className={classes.filter(Boolean).join(" ")}>
                {filled ? digits[i] : isNext ? <span className="code-slot-caret" /> : null}
              </span>
            </span>
          );
        })}
      </div>
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

            {/* Always present, never the largest thing on screen. Not disabled
                when the camera is unavailable — tapping it is how a guard
                learns the camera is the problem. */}
            <button type="button" className="scan-strip" onClick={() => setScanning(true)}>
              <span className="scan-strip-thumb" aria-hidden="true">
                <span className="scan-strip-corner tl" />
                <span className="scan-strip-corner tr" />
                <span className="scan-strip-corner bl" />
                <span className="scan-strip-corner br" />
              </span>
              <span className="scan-strip-text">
                <span className="scan-strip-title">Scan the QR instead</span>
                <span className="scan-strip-sub">Opens the camera full screen</span>
              </span>
              <span className="scan-strip-open">Open</span>
            </button>

            {lastCheckIn ? (
              <>
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
                  {/* Names the path taken and shows the server's timestamp —
                      the record that actually counts (principle #2). */}
                  <p className="checkin-provenance">
                    Server-stamped {new Date(lastCheckIn.enteredAt).toLocaleTimeString()}
                  </p>
                </div>

                <button type="button" className="next-code-button" onClick={nextCode}>
                  Next code
                </button>
              </>
            ) : (
              <form onSubmit={handleCodeSubmit} className="code-entry">
                {outcome && (
                  <div className={`code-outcome ${outcome.kind}`} role="alert">
                    <span className="code-outcome-heading">{outcome.heading}</span>
                    <span className="code-outcome-detail">{outcome.detail}</span>
                    <span className="code-outcome-note">Nothing is blocked · log a walk-in and it is reviewed later</span>
                  </div>
                )}

                <div className="code-field">
                  <span className="eyebrow">Visitor's 6-digit code</span>
                  {/* The real control: focusable, accepts a hardware keyboard
                      and assistive tech. inputMode="none" keeps the OS
                      keyboard from covering the screen — the built keypad
                      below guarantees a tap target the OS one does not. */}
                  <input
                    className="code-input-hidden"
                    type="text"
                    inputMode="none"
                    autoComplete="off"
                    aria-label="Visitor's 6-digit code"
                    value={digits}
                    maxLength={CODE_LENGTH}
                    onChange={(e) => {
                      setOutcome(null);
                      setDigits(e.target.value.replace(/\D/g, "").slice(0, CODE_LENGTH));
                    }}
                  />
                  {codeSlots()}
                </div>

                {/* Sits between the code and the keypad, in the eye path,
                    because it must be filled before submit. */}
                <input
                  type="text"
                  className="guard-input reg-input-compact"
                  value={vehicleRegistration}
                  onChange={(e) => setVehicleRegistration(e.target.value.toUpperCase())}
                  placeholder="Vehicle reg, if there is one"
                  aria-label="Vehicle registration, if there is one"
                />

                <div className="keypad">
                  {["1", "2", "3", "4", "5", "6", "7", "8", "9"].map((d) => (
                    <button key={d} type="button" onClick={() => pressDigit(d)}>
                      {d}
                    </button>
                  ))}
                  <button type="button" className="keypad-action" onClick={pressClear}>
                    Clear
                  </button>
                  <button type="button" onClick={() => pressDigit("0")}>
                    0
                  </button>
                  <button type="button" className="keypad-action" onClick={pressBack}>
                    Back
                  </button>
                </div>

                {/* The button is the progress indicator, so nothing else has
                    to be. Deliberately no auto-submit on the sixth digit — a
                    mistyped last digit would fire a request and burn a
                    rate-limit attempt before the guard could look at it. */}
                <button type="submit" className={`code-submit${codeReady ? " ready" : ""}`} disabled={!codeReady || checkInBusy}>
                  {checkInBusy
                    ? "Checking…"
                    : codeReady
                      ? "Check in"
                      : `Enter ${remaining} more digit${remaining === 1 ? "" : "s"}`}
                </button>
              </form>
            )}

            <Link
              to={walkInHref}
              className="walk-in-escape"
              style={{ display: "flex", alignItems: "center", justifyContent: "center", textDecoration: "none" }}
            >
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

                {!trimmedQuery && checkedOutToday && (
                  <div>
                    <span className="eyebrow">Checked out today</span>
                    <div className="row-list" style={{ marginTop: 8 }}>
                      {checkedOutToday.length === 0 ? (
                        <p className="empty" style={{ padding: 14 }}>
                          No one has checked out yet today.
                        </p>
                      ) : (
                        checkedOutToday.map((entry) => (
                          <div key={entry.id} className="row-list-item">
                            <span className="row-list-item-title">{entry.visitorName}</span>
                            <span className="row-list-item-detail">
                              {entry.category}
                              {entry.vehicleRegistration && ` · ${entry.vehicleRegistration}`}
                              {" · in "}
                              {new Date(entry.enteredAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}
                              {" · out "}
                              {entry.exitedAt &&
                                new Date(entry.exitedAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}
                            </span>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                )}
              </>
            )}
          </>
        )}

        {segment === "vehicles" && <VehicleHistoryContent />}
      </div>

      {/* Full screen, not a 246px inline box — a scan is a whole-attention
          action. QrScanner itself is unchanged; only where it mounts changed. */}
      {scanning && (
        <div className="scanner-fullscreen" role="dialog" aria-modal="true" aria-label="Scan a QR code">
          <QrScanner onDecode={(decoded) => submitCheckIn(decoded, false)} />
          <button type="button" className="scanner-fullscreen-close" onClick={() => setScanning(false)}>
            Type the code instead
          </button>
        </div>
      )}
    </Layout>
  );
}
