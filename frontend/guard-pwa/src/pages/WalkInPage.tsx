import { useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type { VisitorCategory, VisitorEntryResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

const CATEGORIES: VisitorCategory[] = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF"];

export default function WalkInPage() {
  const { auth, setPropertyId } = useAuth();
  const [visitorName, setVisitorName] = useState("");
  const [visitorPhone, setVisitorPhone] = useState("");
  const [purpose, setPurpose] = useState("");
  const [category, setCategory] = useState<VisitorCategory>("VISITOR");
  const [error, setError] = useState<string | null>(null);
  const [lastEntry, setLastEntry] = useState<VisitorEntryResponse | null>(null);
  const [busy, setBusy] = useState(false);

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
        },
      });
      setLastEntry(entry);
      setPropertyId(entry.propertyId);
      setVisitorName("");
      setVisitorPhone("");
      setPurpose("");
      setCategory("VISITOR");
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
            {lastEntry.notes && ` · ${lastEntry.notes}`}
            {" · "}
            {new Date(lastEntry.enteredAt).toLocaleTimeString()}
          </p>
        </div>
      )}
    </Layout>
  );
}
