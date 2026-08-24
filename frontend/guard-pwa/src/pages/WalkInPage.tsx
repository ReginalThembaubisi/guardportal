import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { UnitResponse, VisitorCategory, VisitorEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

const CATEGORIES: VisitorCategory[] = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF"];

export default function WalkInPage() {
  const { auth, setPropertyId } = useAuth();
  const [visitorName, setVisitorName] = useState("");
  const [visitorPhone, setVisitorPhone] = useState("");
  const [purpose, setPurpose] = useState("");
  const [category, setCategory] = useState<VisitorCategory>("VISITOR");
  const [vehicleRegistration, setVehicleRegistration] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [lastEntry, setLastEntry] = useState<VisitorEntryResponse | null>(null);
  const [busy, setBusy] = useState(false);

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
      const entry = await apiFetch<VisitorEntryResponse>("/api/v1/visitor-entries/walk-in", {
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
          <p className="entry-meta">
            {lastEntry.category}
            {lastEntry.vehicleRegistration && ` · ${lastEntry.vehicleRegistration}`}
            {lastEntry.unitId && ` · Unit ${units.find((u) => u.id === lastEntry.unitId)?.unitNumber ?? lastEntry.unitId}`}
            {lastEntry.notes && ` · ${lastEntry.notes}`}
            {" · "}
            {new Date(lastEntry.enteredAt).toLocaleTimeString()}
          </p>
        </div>
      )}
    </Layout>
  );
}
