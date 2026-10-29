import "server-only";

import { cookies } from "next/headers";

/**
 * Server-side fetch to the backend over the internal network. When `withSession` is set, the
 * incoming request's cookies are forwarded so the backend authorizes the real user; the backend
 * remains the only authority for access decisions.
 */
const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

export class ServerApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

export async function serverFetch<T>(path: string, options: { withSession?: boolean } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.withSession) {
    const jar = await cookies();
    const cookieHeader = jar
      .getAll()
      .map((c) => `${c.name}=${encodeURIComponent(c.value)}`)
      .join("; ");
    if (cookieHeader) {
      headers.Cookie = cookieHeader;
    }
  }
  const res = await fetch(`${API}/api/v1${path}`, { headers, cache: "no-store" });
  if (!res.ok) {
    throw new ServerApiError(res.status, `API ${path} responded ${res.status}`);
  }
  return (await res.json()) as T;
}

/** Like serverFetch, but returns null for 404 so pages can call notFound(). */
export async function serverFetchOrNull<T>(path: string, options: { withSession?: boolean } = {}): Promise<T | null> {
  try {
    return await serverFetch<T>(path, options);
  } catch (e) {
    if (e instanceof ServerApiError && e.status === 404) {
      return null;
    }
    throw e;
  }
}
