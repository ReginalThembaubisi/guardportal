import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type { AuthResponse, OtpRequestResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";

type Mode = "resident" | "staff";

export default function LoginPage() {
  const [mode, setMode] = useState<Mode>("resident");
  const [error, setError] = useState<string | null>(null);
  const { login } = useAuth();
  const navigate = useNavigate();

  function handleLoggedIn(response: AuthResponse) {
    login(response);
    if (response.roles.includes("RESIDENT")) {
      navigate("/history");
    } else if (response.roles.includes("PROPERTY_MANAGER")) {
      navigate("/dashboard");
    } else if (response.roles.includes("SUPERVISOR")) {
      navigate("/incidents");
    } else if (response.roles.includes("ADMIN")) {
      navigate("/admin/properties");
    } else if (response.roles.includes("CLIENT")) {
      navigate("/residents");
    } else {
      navigate("/");
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Property Security Platform</h1>
        <div className="tabs">
          <button className={mode === "resident" ? "tab active" : "tab"} onClick={() => setMode("resident")}>
            Resident
          </button>
          <button className={mode === "staff" ? "tab active" : "tab"} onClick={() => setMode("staff")}>
            Staff / Property Manager
          </button>
        </div>
        {error && <p className="error">{error}</p>}
        {mode === "resident" ? (
          <ResidentLogin onSuccess={handleLoggedIn} onError={setError} />
        ) : (
          <StaffLogin onSuccess={handleLoggedIn} onError={setError} />
        )}
      </div>
    </div>
  );
}

function ResidentLogin({
  onSuccess,
  onError,
}: {
  onSuccess: (r: AuthResponse) => void;
  onError: (message: string | null) => void;
}) {
  const [phoneNumber, setPhoneNumber] = useState("");
  const [code, setCode] = useState("");
  const [codeSent, setCodeSent] = useState(false);
  const [devCode, setDevCode] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function requestOtp(e: FormEvent) {
    e.preventDefault();
    onError(null);
    setBusy(true);
    try {
      const response = await apiFetch<OtpRequestResponse>("/api/v1/auth/otp/request", {
        method: "POST",
        body: { phoneNumber },
      });
      setCodeSent(true);
      setDevCode(response.devOnlyCode);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Failed to request a code");
    } finally {
      setBusy(false);
    }
  }

  async function verifyOtp(e: FormEvent) {
    e.preventDefault();
    onError(null);
    setBusy(true);
    try {
      const response = await apiFetch<AuthResponse>("/api/v1/auth/otp/verify", {
        method: "POST",
        body: { phoneNumber, code },
      });
      onSuccess(response);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Invalid code");
    } finally {
      setBusy(false);
    }
  }

  if (!codeSent) {
    return (
      <form onSubmit={requestOtp}>
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
        <button type="submit" disabled={busy}>
          {busy ? "Sending…" : "Send code"}
        </button>
      </form>
    );
  }

  return (
    <form onSubmit={verifyOtp}>
      {devCode && <p className="dev-hint">Dev mode — your code is {devCode}</p>}
      <label>
        Enter the 6-digit code
        <input
          type="text"
          inputMode="numeric"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="123456"
          required
        />
      </label>
      <button type="submit" disabled={busy}>
        {busy ? "Verifying…" : "Log in"}
      </button>
    </form>
  );
}

function StaffLogin({
  onSuccess,
  onError,
}: {
  onSuccess: (r: AuthResponse) => void;
  onError: (message: string | null) => void;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    onError(null);
    setBusy(true);
    try {
      const response = await apiFetch<AuthResponse>("/api/v1/auth/login", {
        method: "POST",
        body: { email, password },
      });
      onSuccess(response);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <label>
        Email
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <label>
        Password
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
      </label>
      <button type="submit" disabled={busy}>
        {busy ? "Logging in…" : "Log in"}
      </button>
    </form>
  );
}
