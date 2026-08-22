import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type { AuthResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const response = await apiFetch<AuthResponse>("/api/v1/auth/login", {
        method: "POST",
        body: { email, password },
      });
      if (!response.roles.includes("GUARD")) {
        setError("This account isn't a guard account.");
        return;
      }
      login(response);
      navigate("/clock");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Guard Check-in</h1>
        {error && <p className="error">{error}</p>}
        <form onSubmit={submit}>
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          <button type="submit" disabled={busy}>
            {busy ? "Logging in…" : "Log in"}
          </button>
        </form>
      </div>
    </div>
  );
}
