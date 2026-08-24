import type { ApiErrorBody } from "./types";

// Relative by default (same-origin /api/...) so this works whether the app is
// opened via localhost or through a tunnel from another device — a hardcoded
// http://localhost:8080 only ever resolves on the developer's own machine,
// since "localhost" is relative to whichever device makes the request. The
// dev server proxies /api to the real backend (see vite.config.ts).
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  token?: string | null;
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (options.token) {
    headers["Authorization"] = `Bearer ${options.token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const body = data as ApiErrorBody | undefined;
    throw new ApiError(response.status, body?.error ?? `Request failed with status ${response.status}`);
  }

  return data as T;
}

/** For endpoints behind auth that return raw bytes (e.g. incident photos) — a plain <img src> can't send an Authorization header. */
export async function apiFetchBlob(path: string, token: string): Promise<string> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new ApiError(response.status, `Failed to load file (status ${response.status})`);
  }
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}
