import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "../../api/client";
import type { PropertyResponse, UserResponse } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Layout from "../../components/Layout";

type LinkRole = "PROPERTY_MANAGER" | "SUPERVISOR";

const LINK_PATHS: Record<LinkRole, string> = {
  PROPERTY_MANAGER: "/api/v1/property-managers",
  SUPERVISOR: "/api/v1/property-supervisors",
};

export default function AdminLinkStaffPage() {
  const { auth } = useAuth();
  const [staff, setStaff] = useState<UserResponse[] | null>(null);
  const [properties, setProperties] = useState<PropertyResponse[] | null>(null);
  const [linkRole, setLinkRole] = useState<LinkRole>("PROPERTY_MANAGER");
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  const [selectedPropertyId, setSelectedPropertyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!auth) return;
    apiFetch<UserResponse[]>("/api/v1/staff", { token: auth.token })
      .then(setStaff)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load staff"));
    apiFetch<PropertyResponse[]>("/api/v1/properties", { token: auth.token })
      .then((props) => {
        setProperties(props);
        if (props.length > 0) setSelectedPropertyId(props[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load properties"));
  }, [auth]);

  const eligibleUsers = (staff ?? []).filter((u) => u.roles.includes(linkRole));

  useEffect(() => {
    setSelectedUserId(eligibleUsers.length > 0 ? eligibleUsers[0].id : null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [linkRole, staff]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!auth || selectedUserId === null || selectedPropertyId === null) return;
    setError(null);
    setSuccess(null);
    setBusy(true);
    try {
      await apiFetch(LINK_PATHS[linkRole], {
        method: "POST",
        token: auth.token,
        body: { userId: selectedUserId, propertyId: selectedPropertyId },
      });
      setSuccess("Linked successfully.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to link");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Layout title="Admin — Link Staff to Property">
      {error && <p className="error">{error}</p>}
      {success && <p className="dev-hint">{success}</p>}

      {staff && properties && properties.length === 0 && (
        <p className="empty">No properties yet — create one first.</p>
      )}

      {staff && properties && properties.length > 0 && (
        <form onSubmit={submit}>
          <label>
            Role
            <select value={linkRole} onChange={(e) => setLinkRole(e.target.value as LinkRole)}>
              <option value="PROPERTY_MANAGER">PROPERTY_MANAGER</option>
              <option value="SUPERVISOR">SUPERVISOR</option>
            </select>
          </label>

          {eligibleUsers.length === 0 && (
            <p className="empty">No staff accounts with the {linkRole} role yet — create one first.</p>
          )}
          {eligibleUsers.length > 0 && (
            <label>
              Staff member
              <select value={selectedUserId ?? ""} onChange={(e) => setSelectedUserId(Number(e.target.value))}>
                {eligibleUsers.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName} ({u.email})
                  </option>
                ))}
              </select>
            </label>
          )}

          <label>
            Property
            <select value={selectedPropertyId ?? ""} onChange={(e) => setSelectedPropertyId(Number(e.target.value))}>
              {properties.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>

          <button type="submit" disabled={busy || eligibleUsers.length === 0}>
            {busy ? "Linking…" : "Link to property"}
          </button>
        </form>
      )}
    </Layout>
  );
}
