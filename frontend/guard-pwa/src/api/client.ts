import type { ApiErrorBody } from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

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
  const headers: Record<string, string> = {};
  if (options.token) {
    headers["Authorization"] = `Bearer ${options.token}`;
  }

  // FormData (multipart uploads) must not be JSON-stringified, and the
  // browser needs to set its own Content-Type with the multipart boundary
  // — setting one here would strip that boundary and break parsing server-side.
  const isFormData = options.body instanceof FormData;
  let body: BodyInit | undefined;
  if (isFormData) {
    body = options.body as FormData;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers,
    body,
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
