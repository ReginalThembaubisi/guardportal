import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../../api/client";
import type { Role, UserResponse } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Layout from "../../components/Layout";

const STAFF_ROLES: Role[] = ["PROPERTY_MANAGER", "SUPERVISOR", "ADMIN"];

export default function AdminStaffPage() {
  const { auth } = useAuth();
  const [staff, setStaff] = useState<UserResponse[] | null>(null);
  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("PROPERTY_MANAGER");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function loadStaff() {
    if (!auth) return;
    apiFetch<UserResponse[]>("/api/v1/staff", { token: auth.token })
      .then((all) => setStaff(all.filter((u) => u.roles.some((r) => STAFF_ROLES.includes(r)))))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load staff"));
  }

  useEffect(loadStaff, [auth]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth) return;
    setError(null);
    setBusy(true);
    try {
      await apiFetch("/api/v1/staff", {
        method: "POST",
        token: auth.token,
        body: {
          fullName: fullName.trim(),
          phoneNumber: phoneNumber.trim(),
          email: email.trim(),
          password,
          role,
        },
      });
      setFullName("");
      setPhoneNumber("");
      setEmail("");
      setPassword("");
      setRole("PROPERTY_MANAGER");
      loadStaff();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create staff account");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Admin — Staff">
      {error && <p className="error">{error}</p>}

      <form onSubmit={submit}>
        <label>
          Full name
          <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} required />
        </label>
        <label>
          Phone number
          <input type="tel" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="+27821234567" required />
        </label>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Initial password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            placeholder="At least 8 characters"
            required
          />
        </label>
        <label>
          Role
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {STAFF_ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Creating…" : "Create staff account"}
        </button>
      </form>

      <h2 style={{ marginTop: 24 }}>Staff accounts</h2>
      {staff && staff.length === 0 && <p className="empty">No staff accounts yet.</p>}
      {staff && staff.length > 0 && (
        <table className="entries-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Email</th>
              <th>Roles</th>
            </tr>
          </thead>
          <tbody>
            {staff.map((u) => (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td>{u.fullName}</td>
                <td>{u.email ?? "—"}</td>
                <td>{u.roles.join(", ")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Layout>
  );
}
