import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type {
  GuardResponse,
  PropertySupervisorResponse,
  ShiftScheduleImportResponse,
  ShiftScheduleImportRow,
  ShiftScheduleResponse,
  ShiftType,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

/**
 * RFC 4180-ish CSV parser — same as ResidentsPage.tsx's parseCsvTable:
 * quoted fields, commas/newlines inside a quoted field, "" as an escaped
 * literal quote.
 */
function parseCsvTable(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let inQuotes = false;
  let i = 0;

  while (i < text.length) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 2;
        } else {
          inQuotes = false;
          i++;
        }
      } else {
        field += c;
        i++;
      }
      continue;
    }
    if (c === '"') {
      inQuotes = true;
      i++;
    } else if (c === ",") {
      row.push(field);
      field = "";
      i++;
    } else if (c === "\r") {
      i++;
    } else if (c === "\n") {
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
      i++;
    } else {
      field += c;
      i++;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows.filter((r) => !(r.length === 1 && r[0] === ""));
}

/** Fixed column order: header row, then guardPhoneNumber,shiftDate,shiftType,startTime,endTime (times may be blank). */
function parseShiftScheduleCsv(text: string): ShiftScheduleImportRow[] {
  const table = parseCsvTable(text);
  const dataRows = table.slice(1);
  return dataRows.map(([guardPhoneNumber, shiftDate, shiftType, startTime, endTime]) => ({
    guardPhoneNumber: (guardPhoneNumber ?? "").trim(),
    shiftDate: (shiftDate ?? "").trim(),
    shiftType: (shiftType ?? "").trim(),
    startTime: startTime?.trim() || undefined,
    endTime: endTime?.trim() || undefined,
  }));
}

const CSV_TEMPLATE = `guardPhoneNumber,shiftDate,shiftType,startTime,endTime
+27821234567,2026-09-01,DAY,06:00,18:00
+27821234568,2026-09-01,NIGHT,,
`;

function downloadCsvTemplate() {
  downloadCsv(CSV_TEMPLATE, "shift-schedule-import-template.csv");
}

const MAX_RANGE_DAYS = 92;

/** Inclusive list of YYYY-MM-DD dates from start to end, done in UTC to avoid local-timezone drift. */
function expandDateRange(start: string, end: string): string[] {
  const [sy, sm, sd] = start.split("-").map(Number);
  const [ey, em, ed] = end.split("-").map(Number);
  const startMs = Date.UTC(sy, sm - 1, sd);
  const endMs = Date.UTC(ey, em - 1, ed);
  const dates: string[] = [];
  for (let ms = startMs; ms <= endMs; ms += 86400000) {
    const d = new Date(ms);
    dates.push(`${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}`);
  }
  return dates;
}

function downloadCsv(text: string, filename: string) {
  const blob = new Blob([text], { type: "text/csv" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Shift roster for a Supervisor's property — replaces sharing shifts over a
 * WhatsApp group. Guards see their own upcoming shifts in the guard app
 * (GET /api/v1/shift-schedules/mine), and clocking in picks up today's row
 * automatically. Structurally mirrors ResidentsPage.tsx's CSV import flow.
 */
export default function ShiftSchedulePage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertySupervisorResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [guards, setGuards] = useState<GuardResponse[] | null>(null);
  const [schedule, setSchedule] = useState<ShiftScheduleResponse[] | null>(null);

  const [selectedGuardId, setSelectedGuardId] = useState<number | null>(null);
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [shiftType, setShiftType] = useState<ShiftType>("DAY");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [removingId, setRemovingId] = useState<number | null>(null);

  const [importBusy, setImportBusy] = useState(false);
  const [importResult, setImportResult] = useState<ShiftScheduleImportResponse | null>(null);

  useEffect(() => {
    if (!auth) return;
    apiFetch<PropertySupervisorResponse[]>("/api/v1/property-supervisors/mine", { token: auth.token })
      .then((props) => {
        setProperties(props);
        if (props.length > 0) setSelectedPropertyId(props[0].propertyId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your properties"));
    apiFetch<GuardResponse[]>("/api/v1/guards", { token: auth.token })
      .then(setGuards)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load guards"));
  }, [auth]);

  const guardsForSelectedProperty = (guards ?? []).filter((g) => g.propertyId === selectedPropertyId);

  useEffect(() => {
    setSelectedGuardId((prev) =>
      prev !== null && guardsForSelectedProperty.some((g) => g.id === prev) ? prev : guardsForSelectedProperty[0]?.id ?? null,
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedPropertyId, guards]);

  function loadSchedule() {
    if (!auth || selectedPropertyId === null) return;
    apiFetch<ShiftScheduleResponse[]>(`/api/v1/shift-schedules?propertyId=${selectedPropertyId}`, { token: auth.token })
      .then(setSchedule)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the shift schedule"));
  }

  useEffect(loadSchedule, [auth, selectedPropertyId]);

  /**
   * A guard often works the same shift every day for a stretch (e.g. nights
   * from the 1st to the 4th) rather than one isolated day — typing that as
   * one CSV row per day is exactly the tedious manual entry this feature is
   * meant to replace. So this expands the date range into one row per day
   * and posts it through the same bulk-import endpoint as the CSV upload,
   * which already reports each day's outcome (e.g. a day that's already
   * scheduled gets skipped with a reason, not silently dropped or aborted).
   */
  async function submitAdd(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedGuardId === null || selectedPropertyId === null) return;
    const guard = guardsForSelectedProperty.find((g) => g.id === selectedGuardId);
    if (!guard) return;

    const effectiveEndDate = endDate || startDate;
    if (effectiveEndDate < startDate) {
      setError("End date can't be before the start date");
      return;
    }
    const dates = expandDateRange(startDate, effectiveEndDate);
    if (dates.length > MAX_RANGE_DAYS) {
      setError(`That range is ${dates.length} days — split it into batches of ${MAX_RANGE_DAYS} or fewer`);
      return;
    }

    setError(null);
    setImportResult(null);
    setBusy(true);
    try {
      const rows: ShiftScheduleImportRow[] = dates.map((shiftDate) => ({
        guardPhoneNumber: guard.phoneNumber,
        shiftDate,
        shiftType,
        startTime: startTime || undefined,
        endTime: endTime || undefined,
      }));
      const result = await apiFetch<ShiftScheduleImportResponse>("/api/v1/shift-schedules/import", {
        method: "POST",
        token: auth.token,
        body: { propertyId: selectedPropertyId, rows },
      });
      setImportResult(result);
      setStartDate("");
      setEndDate("");
      setStartTime("");
      setEndTime("");
      loadSchedule();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to add shift(s)");
    } finally {
      setBusy(false);
    }
  }

  async function removeShift(id: number) {
    if (!auth) return;
    setError(null);
    setRemovingId(id);
    try {
      await apiFetch(`/api/v1/shift-schedules/${id}`, { method: "DELETE", token: auth.token });
      setSchedule((prev) => (prev ? prev.filter((s) => s.id !== id) : prev));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to remove shift");
    } finally {
      setRemovingId(null);
    }
  }

  async function handleCsvSelected(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file || !auth || selectedPropertyId === null) return;
    setError(null);
    setImportResult(null);
    setImportBusy(true);
    try {
      const text = await file.text();
      const rows = parseShiftScheduleCsv(text);
      const result = await apiFetch<ShiftScheduleImportResponse>("/api/v1/shift-schedules/import", {
        method: "POST",
        token: auth.token,
        body: { propertyId: selectedPropertyId, rows },
      });
      setImportResult(result);
      loadSchedule();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Import failed");
    } finally {
      setImportBusy(false);
    }
  }

  return (
    <Layout title="Shift Schedule">
      {error && <p className="error">{error}</p>}

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {properties && properties.length > 1 && (
        <label>
          Property
          <select value={selectedPropertyId ?? ""} onChange={(e) => setSelectedPropertyId(Number(e.target.value))}>
            {properties.map((p) => (
              <option key={p.propertyId} value={p.propertyId}>
                {p.propertyName}
              </option>
            ))}
          </select>
        </label>
      )}

      {properties && properties.length > 0 && (
        <>
          <h2>Upcoming shifts ({schedule?.length ?? 0})</h2>
          {schedule && schedule.length === 0 && <p className="empty">No shifts scheduled for this property yet.</p>}
          {schedule && schedule.length > 0 && (
            <table className="entries-table">
              <thead>
                <tr>
                  <th>Guard</th>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Time</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {schedule.map((s) => (
                  <tr key={s.id}>
                    <td>{s.guardName}</td>
                    <td>{s.shiftDate}</td>
                    <td>{s.shiftType}</td>
                    <td>{s.startTime && s.endTime ? `${s.startTime}–${s.endTime}` : "—"}</td>
                    <td>
                      <button
                        type="button"
                        className="link-button"
                        onClick={() => removeShift(s.id)}
                        disabled={removingId === s.id}
                      >
                        {removingId === s.id ? "Removing…" : "Remove"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <h2 style={{ marginTop: 24 }}>Bulk upload</h2>
          <p className="dev-hint">
            For uploading a whole roster at once instead of sharing it over WhatsApp. CSV with a header
            row, then one row per shift: guard's phone number, date (YYYY-MM-DD), DAY or NIGHT, start
            time and end time (both optional, HH:mm).{" "}
            <button type="button" className="link-button" onClick={downloadCsvTemplate}>
              Download a template
            </button>
          </p>
          <input type="file" accept=".csv,text/csv" onChange={handleCsvSelected} disabled={importBusy} />
          {importBusy && <p className="dev-hint">Importing…</p>}
          {importResult && (
            <div className="invitation-result">
              <h2>
                {importResult.createdCount} added, {importResult.skippedCount} skipped
              </h2>
              {importResult.skippedCount > 0 && (
                <table className="entries-table">
                  <thead>
                    <tr>
                      <th>Row</th>
                      <th>Guard phone</th>
                      <th>Date</th>
                      <th>Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {importResult.rows
                      .filter((r) => !r.created)
                      .map((r) => (
                        <tr key={r.rowNumber}>
                          <td>{r.rowNumber}</td>
                          <td>{r.guardPhoneNumber}</td>
                          <td>{r.shiftDate}</td>
                          <td>{r.reason}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          <h2 style={{ marginTop: 24 }}>Add a shift</h2>
          <p className="dev-hint">
            Set an end date to repeat the same shift every day in between — e.g. nights from the 1st to
            the 4th — instead of adding each day one by one.
          </p>
          <form onSubmit={submitAdd}>
            <label>
              Guard
              {guardsForSelectedProperty.length === 0 ? (
                <p className="empty">No guards on this property yet.</p>
              ) : (
                <select value={selectedGuardId ?? ""} onChange={(e) => setSelectedGuardId(Number(e.target.value))} required>
                  {guardsForSelectedProperty.map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.fullName}
                    </option>
                  ))}
                </select>
              )}
            </label>
            <label>
              Start date
              <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
            </label>
            <label>
              End date (optional — repeats the shift through this date)
              <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} min={startDate || undefined} />
            </label>
            <label>
              Shift type
              <select value={shiftType} onChange={(e) => setShiftType(e.target.value as ShiftType)}>
                <option value="DAY">Day</option>
                <option value="NIGHT">Night</option>
              </select>
            </label>
            <label>
              Start time (optional)
              <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
            </label>
            <label>
              End time (optional)
              <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
            </label>
            <button type="submit" disabled={busy || guardsForSelectedProperty.length === 0}>
              {busy ? "Adding…" : endDate && endDate !== startDate ? "Add shifts" : "Add shift"}
            </button>
          </form>
        </>
      )}
    </Layout>
  );
}
