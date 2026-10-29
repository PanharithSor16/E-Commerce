"use client";

/**
 * Analytics consent and the opaque visitor identifiers.
 * - Nothing is tracked and no identifier exists until the visitor grants analytics consent.
 * - `vid` is a random UUID (no personal data), kept 13 months; `vsid` is the visit (session) id,
 *   rotated after 30 minutes of inactivity. Both are first-party, SameSite=Lax cookies so the
 *   server can attribute trusted server-side events (login, purchase) to the consented visitor.
 */

export interface ConsentState {
  analytics: boolean;
  marketing: boolean;
  version: string;
  decidedAt: string;
}

const STORAGE_KEY = "consent.v1";
const LAST_ACTIVITY_KEY = "vsid.lastActivity";
const SESSION_TIMEOUT_MS = 30 * 60 * 1000;
export const POLICY_VERSION = "1.0";

function readStorage(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null) {
  try {
    if (value === null) {
      window.localStorage.removeItem(key);
    } else {
      window.localStorage.setItem(key, value);
    }
  } catch {
    // storage unavailable (private mode): consent simply is not remembered
  }
}

export function getConsent(): ConsentState | null {
  const raw = readStorage(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as ConsentState;
    // A new policy version requires asking again.
    return parsed.version === POLICY_VERSION ? parsed : null;
  } catch {
    return null;
  }
}

export function hasAnalyticsConsent(): boolean {
  return getConsent()?.analytics === true;
}

export function saveConsent(analytics: boolean, marketing: boolean): ConsentState {
  const state: ConsentState = { analytics, marketing, version: POLICY_VERSION, decidedAt: new Date().toISOString() };
  writeStorage(STORAGE_KEY, JSON.stringify(state));
  if (analytics) {
    getVisitorId();
  } else {
    deleteCookie("vid");
    deleteCookie("vsid");
    writeStorage(LAST_ACTIVITY_KEY, null);
  }
  window.dispatchEvent(new CustomEvent("consent-changed", { detail: state }));
  return state;
}

function readCookie(name: string): string | null {
  const match = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

function writeCookie(name: string, value: string, maxAgeSeconds: number) {
  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${name}=${encodeURIComponent(value)}; Path=/; Max-Age=${maxAgeSeconds}; SameSite=Lax${secure}`;
}

function deleteCookie(name: string) {
  document.cookie = `${name}=; Path=/; Max-Age=0; SameSite=Lax`;
}

/** Returns the visitor id, creating it on first use. Only call after consent. */
export function getVisitorId(): string {
  let vid = readCookie("vid");
  if (!vid) {
    vid = crypto.randomUUID();
  }
  writeCookie("vid", vid, 395 * 24 * 3600);
  return vid;
}

/** Returns the current visit id, starting a new visit after 30 minutes of inactivity. */
export function getSessionId(): { id: string; isNew: boolean } {
  const now = Date.now();
  const last = Number(readStorage(LAST_ACTIVITY_KEY) ?? 0);
  let sid = readCookie("vsid");
  let isNew = false;
  if (!sid || now - last > SESSION_TIMEOUT_MS) {
    sid = crypto.randomUUID();
    isNew = true;
  }
  writeStorage(LAST_ACTIVITY_KEY, String(now));
  writeCookie("vsid", sid, 30 * 60);
  return { id: sid, isNew };
}
