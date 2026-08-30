import { useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type { UnitResponse, VisitorCategory, VisitorWalkInResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";
import Seal from "../components/Seal";

const CATEGORIES: VisitorCategory[] = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF"];

/** 417302 -> "417 302". Matches how Gate displays and how the code is spoken. */
function formatCode(code: string): string {
  return code.length > 3 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;
}

export default function WalkInPage() {
  const { auth, setPropertyId } = useAuth();

  /*
    Gate → Check in links here as /walk-in?reg=…&code=… when a code won't
    clear, so an expired or unknown code doesn't cost the guard a re-type of
    everything they already keyed in. Read once for the initial state only —
    deliberately not a useEffect that re-syncs, or the guard's own edits would
    be overwritten on every render.

    The code is seeded into `purpose` rather than dropped: a visitor who
    presented a code that didn't work is a materially different record from
    one who arrived with nothing, and that difference belongs in the entry
    that goes to passive review. It is prefilled and editable, never hidden —
    the guard can reword or clear it.
  */
  const [searchParams] = useSearchParams();
  const carriedReg = (searchParams.get("reg") ?? "").toUpperCase();
  const carriedCode = (searchParams.get("code") ?? "").replace(/\D/g, "").slice(0, 6);

  const [visitorName, setVisitorName] = useState("");
  const [visitorPhone, setVisitorPhone] = useState("");
  const [purpose, setPurpose] = useState(carriedCode ? `Presented code ${formatCode(carriedCode)} — did not clear` : "");
  const [category, setCategory] = useState<VisitorCategory>("VISITOR");
  const [vehicleRegistration, setVehicleRegistration] = useState(carriedReg);
  const [error, setError] = useState<string | null>(null);
  const [lastEntry, setLastEntry] = useState<VisitorWalkInResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [showCarriedNote, setShowCarriedNote] = useState(Boolean(carriedReg || carriedCode));

  const [units, setUnits] = useState<UnitResponse[]>([]);
  const [unitQuery, setUnitQuery] = useState("");
  const [selectedUnit, setSelectedUnit] = useState<UnitResponse | null>(null);

  useEffect(() => {
    if (!auth || auth.propertyId === null) return;
    apiFetch<UnitResponse[]>(`/api/v1/properties/${auth.propertyId}/units`, { token: auth.token })
      .then(setUnits)
      .catch(() => {
        // Non-fatal — the visitor can still be checked in without a unit link.
      });
  }, [auth]);

  const unitMatches =
    selectedUnit || unitQuery.trim() === ""
      ? []
      : units.filter((u) => u.unitNumber.toLowerCase().includes(unitQuery.trim().toLowerCase())).slice(0, 8);

  function pickUnit(unit: UnitResponse) {
    setSelectedUnit(unit);
    setUnitQuery(unit.unitNumber);
  }

  function clearUnit() {
    setSelectedUnit(null);
    setUnitQuery("");
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      const entry = await apiFetch<VisitorWalkInResponse>("/api/v1/visitor-entries/walk-in", {
        method: "POST",
        token: auth.token,
        body: {
          visitorName: visitorName.trim(),
          visitorPhone: visitorPhone.trim() || undefined,
          category,
          purpose: purpose.trim() || undefined,
          unitId: selectedUnit?.id,
          vehicleRegistration: vehicleRegistration.trim() || undefined,
        },
      });
      setLastEntry(entry);
      setPropertyId(entry.propertyId);
      setVisitorName("");
      setVisitorPhone("");
      setPurpose("");
      setCategory("VISITOR");
      setVehicleRegistration("");
      setShowCarriedNote(false);
      clearUnit();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Walk-in check-in failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Walk-in Visitor">
      {error && <p className="error">{error}</p>}

      {/* Prefill is never silent — say where the values came from. */}
      {showCarriedNote && (
        <div className="carried-note">
          <span className="carried-note-title">Carried over from the gate</span>
          <span className="carried-note-detail">
            {carriedCode && (
              <>
                Code <strong>{formatCode(carriedCode)}</strong>
                {carriedReg && " · "}
              </>
            )}
            {carriedReg && <>Reg <strong>{carriedReg}</strong></>}
          </span>
          <button type="button" className="carried-note-clear" onClick={() => setShowCarriedNote(false)}>
            Hide
          </button>
        </div>
      )}

      <form onSubmit={submit}>
        <label>
          Visitor name
          <input
            type="text"
            value={visitorName}
            onChange={(e) => setVisitorName(e.target.value)}
            placeholder="Full name"
            autoFocus
            required
          />
        </label>
        <label>
          Phone (optional)
          <input
            type="tel"
            value={visitorPhone}
            onChange={(e) => setVisitorPhone(e.target.value)}
            placeholder="+27821234567"
          />
        </label>
        <label>
          Category
          <select value={category} onChange={(e) => setCategory(e.target.value as VisitorCategory)}>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>
        <label>
          Vehicle registration (optional)
          <input
            type="text"
            value={vehicleRegistration}
            onChange={(e) => setVehicleRegistration(e.target.value.toUpperCase())}
            placeholder="e.g. CA123456"
          />
        </label>
        <label>
          Visiting which unit? (optional)
          <div className="unit-picker">
            <input
              type="text"
              value={unitQuery}
              onChange={(e) => {
                setSelectedUnit(null);
                setUnitQuery(e.target.value);
              }}
              placeholder="Type a unit number, e.g. 12"
            />
            {selectedUnit && (
              <button type="button" className="unit-picker-clear" onClick={clearUnit}>
                Clear
              </button>
            )}
          </div>
          {unitMatches.length > 0 && (
            <ul className="unit-picker-results">
              {unitMatches.map((u) => (
                <li key={u.id}>
                  <button type="button" onClick={() => pickUnit(u)}>
                    Unit {u.unitNumber}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </label>
        <label>
          Purpose (optional)
          <input
            type="text"
            value={purpose}
            onChange={(e) => setPurpose(e.target.value)}
            placeholder="e.g. Dropping off a parcel"
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Checking in…" : "Check in"}
        </button>
      </form>

      {lastEntry && (
        <div className="checkin-result pending">
          <h2>Checked in — pending review</h2>
          <p className="checkin-visitor-name">{lastEntry.visitorName}</p>
          {lastEntry.visitingResidentNames ? (
            <p className="checkin-visiting">Visiting {lastEntry.visitingResidentNames}</p>
          ) : (
            lastEntry.unitId && (
              <p className="checkin-visiting">
                Unit {units.find((u) => u.id === lastEntry.unitId)?.unitNumber ?? lastEntry.unitId} — no resident on file yet
              </p>
            )
          )}
          <p className="entry-meta">
            {lastEntry.category}
            {lastEntry.vehicleRegistration && (
              <>
                {" "}
                · {lastEntry.vehicleRegistration}
                {lastEntry.vehicleRecognized && (
                  <>
                    {" "}
                    <Seal state="cleared">
                      Recognized{lastEntry.recognizedVehicleOwnerName && ` — ${lastEntry.recognizedVehicleOwnerName}'s`}
                    </Seal>
                  </>
                )}
              </>
            )}
            {lastEntry.notes && ` · ${lastEntry.notes}`}
            {" · "}
            {new Date(lastEntry.enteredAt).toLocaleTimeString()}
          </p>
        </div>
      )}
    </Layout>
  );
}
