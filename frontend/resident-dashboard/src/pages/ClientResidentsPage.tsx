import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { apiFetch, ApiError } from "../api/client";
import type {
  PropertyClientResponse,
  ResidentImportResponse,
  ResidentImportRow,
  ResidentResponse,
  UnitResponse,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Layout from "../components/Layout";

/**
 * RFC 4180-ish CSV parser: quoted fields, commas and newlines inside a
 * quoted field, "" as an escaped literal quote. Parses the whole file
 * character-by-character rather than splitting on \n first, since a
 * quoted field is allowed to contain a real newline.
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

/**
 * Our own fixed column order, not an attempt to auto-detect arbitrary
 * spreadsheet layouts — every residence keeps its existing list
 * differently, so this demonstrates onboarding with one known shape:
 * a header row, then unitNumber,fullName,phoneNumber,email (email may
 * be blank).
 */
function parseResidentCsv(text: string): ResidentImportRow[] {
  const table = parseCsvTable(text);
  const dataRows = table.slice(1); // skip header row
  return dataRows.map(([unitNumber, fullName, phoneNumber, email]) => ({
    unitNumber: (unitNumber ?? "").trim(),
    fullName: (fullName ?? "").trim(),
    phoneNumber: (phoneNumber ?? "").trim(),
    email: email?.trim() || undefined,
  }));
}

const PAGE_SIZE = 25;

const CSV_TEMPLATE = `unitNumber,fullName,phoneNumber,email
101,Jane Student,+27821234567,jane@example.com
102,John Student,+27821234568,
`;

function downloadCsvTemplate() {
  downloadCsv(CSV_TEMPLATE, "resident-import-template.csv");
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

/** Quotes a field only when it needs it (contains a comma, quote, or newline) — matches parseCsvTable's "" escaping. */
function toCsvField(value: string): string {
  if (/[",\n]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

/** Same column order as the import template, so an exported file can be re-imported unchanged. */
function residentsToCsv(list: ResidentResponse[]): string {
  const header = "unitNumber,fullName,phoneNumber,email";
  const rows = list.map((r) =>
    [r.unitNumber, r.fullName, r.phoneNumber, r.email ?? ""].map(toCsvField).join(","),
  );
  return [header, ...rows].join("\n") + "\n";
}

export default function ClientResidentsPage() {
  const { auth } = useAuth();
  const [properties, setProperties] = useState<PropertyClientResponse[] | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [units, setUnits] = useState<UnitResponse[] | null>(null);
  const [residents, setResidents] = useState<ResidentResponse[] | null>(null);

  const [selectedUnitId, setSelectedUnitId] = useState<number | null>(null);
  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [removingId, setRemovingId] = useState<number | null>(null);

  const [importBusy, setImportBusy] = useState(false);
  const [importResult, setImportResult] = useState<ResidentImportResponse | null>(null);

  const [searchQuery, setSearchQuery] = useState("");
  const [page, setPage] = useState(1);

  useEffect(() => {
    if (!auth) return;
    apiFetch<PropertyClientResponse[]>("/api/v1/property-clients/mine", { token: auth.token })
      .then((props) => {
        setProperties(props);
        if (props.length > 0) setSelectedPropertyId(props[0].propertyId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your properties"));
  }, [auth]);

  function loadUnits() {
    if (!auth || selectedPropertyId === null) return;
    apiFetch<UnitResponse[]>(`/api/v1/properties/${selectedPropertyId}/units`, { token: auth.token })
      .then((u) => {
        setUnits(u);
        setSelectedUnitId((prev) => (prev !== null && u.some((x) => x.id === prev) ? prev : u[0]?.id ?? null));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load units"));
  }

  useEffect(loadUnits, [auth, selectedPropertyId]);
  useEffect(() => setPage(1), [selectedPropertyId]);

  function loadResidents() {
    if (!auth) return;
    apiFetch<ResidentResponse[]>("/api/v1/residents", { token: auth.token })
      .then(setResidents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load residents"));
  }

  useEffect(loadResidents, [auth]);

  // The API returns every resident across all properties this client owns —
  // narrow to whichever property is currently selected using the unit ids
  // already scoped to it (ResidentResponse doesn't carry a propertyId itself).
  const unitIdsForSelectedProperty = new Set((units ?? []).map((u) => u.id));
  const residentsForSelectedProperty = (residents ?? []).filter((r) => unitIdsForSelectedProperty.has(r.unitId));

  const trimmedQuery = searchQuery.trim().toLowerCase();
  const visibleResidents = trimmedQuery
    ? residentsForSelectedProperty.filter(
        (r) =>
          r.fullName.toLowerCase().includes(trimmedQuery) ||
          r.unitNumber.toLowerCase().includes(trimmedQuery) ||
          r.phoneNumber.toLowerCase().includes(trimmedQuery) ||
          (r.email ?? "").toLowerCase().includes(trimmedQuery),
      )
    : residentsForSelectedProperty;

  const totalPages = Math.max(1, Math.ceil(visibleResidents.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pagedResidents = visibleResidents.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  function updateSearch(value: string) {
    setSearchQuery(value);
    setPage(1);
  }

  async function submitAdd(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedUnitId === null) return;
    setError(null);
    setBusy(true);
    try {
      await apiFetch<ResidentResponse>("/api/v1/residents", {
        method: "POST",
        token: auth.token,
        body: {
          unitId: selectedUnitId,
          fullName: fullName.trim(),
          phoneNumber: phoneNumber.trim(),
          email: email.trim() || undefined,
        },
      });
      setFullName("");
      setPhoneNumber("");
      setEmail("");
      loadResidents();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to add resident");
    } finally {
      setBusy(false);
    }
  }

  async function removeResident(id: number) {
    if (!auth) return;
    setError(null);
    setRemovingId(id);
    try {
      await apiFetch(`/api/v1/residents/${id}`, { method: "DELETE", token: auth.token });
      setResidents((prev) => (prev ? prev.filter((r) => r.id !== id) : prev));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to remove resident");
    } finally {
      setRemovingId(null);
    }
  }

  async function handleCsvSelected(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow re-selecting the same file after a fix
    if (!file || !auth || selectedPropertyId === null) return;
    setError(null);
    setImportResult(null);
    setImportBusy(true);
    try {
      const text = await file.text();
      const rows = parseResidentCsv(text);
      const result = await apiFetch<ResidentImportResponse>("/api/v1/residents/import", {
        method: "POST",
        token: auth.token,
        body: { propertyId: selectedPropertyId, rows },
      });
      setImportResult(result);
      loadResidents();
      loadUnits(); // rows can create new units
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Import failed");
    } finally {
      setImportBusy(false);
    }
  }

  return (
    <Layout title="Residents">
      {error && <p className="error">{error}</p>}

      {properties && properties.length === 0 && (
        <p className="empty">You aren't linked to any property yet. Ask an admin to link your account.</p>
      )}

      {properties && properties.length > 1 && (
        <label>
          Property
          <select
            value={selectedPropertyId ?? ""}
            onChange={(e) => setSelectedPropertyId(Number(e.target.value))}
          >
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
          <h2>Current residents ({residentsForSelectedProperty.length})</h2>
          {residentsForSelectedProperty.length > 0 && (
            <>
              <input
                type="search"
                value={searchQuery}
                onChange={(e) => updateSearch(e.target.value)}
                placeholder="Search by name, unit, phone, or email"
              />
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  const propertyName = properties?.find((p) => p.propertyId === selectedPropertyId)?.propertyName ?? "export";
                  const safeName = propertyName.replace(/[^a-z0-9]+/gi, "-").toLowerCase();
                  downloadCsv(residentsToCsv(visibleResidents), `residents-${safeName}.csv`);
                }}
              >
                Export {trimmedQuery ? `${visibleResidents.length} matching` : "all"} to CSV
              </button>
            </>
          )}
          {residentsForSelectedProperty.length === 0 ? (
            <p className="empty">No residents on file for this property yet.</p>
          ) : visibleResidents.length === 0 ? (
            <p className="empty">No residents match "{searchQuery.trim()}".</p>
          ) : (
            <>
              <table className="entries-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Unit</th>
                    <th>Phone</th>
                    <th>Email</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {pagedResidents.map((r) => (
                    <tr key={r.id}>
                      <td>{r.fullName}</td>
                      <td>{r.unitNumber}</td>
                      <td>{r.phoneNumber}</td>
                      <td>{r.email ?? "—"}</td>
                      <td>
                        <button
                          type="button"
                          className="link-button"
                          onClick={() => removeResident(r.id)}
                          disabled={removingId === r.id}
                        >
                          {removingId === r.id ? "Removing…" : "Remove"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {totalPages > 1 && (
                <div className="pagination">
                  <button type="button" onClick={() => setPage((p) => p - 1)} disabled={currentPage <= 1}>
                    Previous
                  </button>
                  <span className="dev-hint">
                    Page {currentPage} of {totalPages}
                  </span>
                  <button type="button" onClick={() => setPage((p) => p + 1)} disabled={currentPage >= totalPages}>
                    Next
                  </button>
                </div>
              )}
            </>
          )}

          <h2 style={{ marginTop: 24 }}>Bulk import</h2>
          <p className="dev-hint">
            For onboarding an existing residence at once. CSV with a header row, then one row per
            resident: unit number, full name, phone number, email (email may be left blank). A unit
            number that doesn't exist yet is created automatically.{" "}
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
                      <th>Unit</th>
                      <th>Name</th>
                      <th>Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {importResult.rows
                      .filter((r) => !r.created)
                      .map((r) => (
                        <tr key={r.rowNumber}>
                          <td>{r.rowNumber}</td>
                          <td>{r.unitNumber}</td>
                          <td>{r.fullName}</td>
                          <td>{r.reason}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          <h2 style={{ marginTop: 24 }}>Add a resident</h2>
          <form onSubmit={submitAdd}>
            <label>
              Unit
              {units && units.length === 0 ? (
                <p className="empty">No units on this property yet. Ask an admin to add one.</p>
              ) : (
                <select value={selectedUnitId ?? ""} onChange={(e) => setSelectedUnitId(Number(e.target.value))} required>
                  {units?.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.unitNumber}
                    </option>
                  ))}
                </select>
              )}
            </label>
            <label>
              Full name
              <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} required />
            </label>
            <label>
              Phone number
              <input
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="+27821234567"
                required
              />
            </label>
            <label>
              Email (optional)
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <button type="submit" disabled={busy || !units || units.length === 0}>
              {busy ? "Adding…" : "Add resident"}
            </button>
          </form>
        </>
      )}
    </Layout>
  );
}
