"use client";

import axios, { AxiosError } from "axios";

import type { ProblemDetail } from "./types";

/**
 * Browser API client. Requests go to the same origin (`/api/v1`), so the HTTP-only session cookie is
 * sent automatically. Axios copies the readable XSRF-TOKEN cookie into the X-XSRF-TOKEN header
 * for same-origin requests, which satisfies Spring Security's CSRF check.
 */
export const api = axios.create({
  baseURL: "/api/v1",
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  timeout: 20_000,
  headers: { Accept: "application/json" },
});

/** Normalized error carrying the server's problem details. */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors: Record<string, string>;
  readonly traceId?: string;

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? "Request failed");
    this.status = problem.status;
    this.code = problem.code;
    this.traceId = problem.traceId;
    this.fieldErrors = Object.fromEntries((problem.errors ?? []).map((e) => [e.field, e.message]));
  }
}

let csrfPromise: Promise<unknown> | null = null;

/** Ensures the XSRF-TOKEN cookie exists before the first state-changing request. */
export function ensureCsrf(): Promise<unknown> {
  if (typeof document !== "undefined" && document.cookie.includes("XSRF-TOKEN=")) {
    return Promise.resolve();
  }
  csrfPromise ??= api.get("/auth/csrf").finally(() => {
    csrfPromise = null;
  });
  return csrfPromise;
}

api.interceptors.request.use(async (config) => {
  const method = (config.method ?? "get").toLowerCase();
  if (!["get", "head", "options"].includes(method) && !config.url?.endsWith("/auth/csrf")) {
    await ensureCsrf();
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const original = error.config as (typeof error.config & { _csrfRetried?: boolean }) | undefined;
    // A rotated or missing CSRF token: fetch a fresh one and retry once.
    if (error.response?.status === 403 && error.response.data?.code === "CSRF_INVALID" && original && !original._csrfRetried) {
      original._csrfRetried = true;
      await api.get("/auth/csrf");
      return api.request(original);
    }
    if (error.response?.data && typeof error.response.data === "object") {
      return Promise.reject(new ApiError({ ...error.response.data, status: error.response.status }));
    }
    return Promise.reject(
      new ApiError({
        status: error.response?.status ?? 0,
        detail: error.code === "ECONNABORTED" ? "The request timed out." : "Network error. Please try again.",
      }),
    );
  },
);

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Something went wrong. Please try again.";
}

/** A fresh key per checkout attempt; retries of the same submission reuse it. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
