"use client";

import { getSessionId, getVisitorId, hasAnalyticsConsent } from "./consent";

/**
 * Client event tracker. Only allowlisted fields are sent; the backend validates and sanitizes
 * again. PURCHASE / LOGIN / LOGOUT / REGISTER are recorded by the server and never sent from here.
 */
export type ClientEventType =
  | "PAGE_VIEW" | "PRODUCT_VIEW" | "CATEGORY_VIEW" | "SEARCH" | "ADD_TO_CART" | "REMOVE_FROM_CART" | "BEGIN_CHECKOUT";

export interface TrackProps {
  productId?: number;
  categoryId?: number;
  query?: string;
  resultsCount?: number;
  variantId?: number;
  quantity?: number;
  itemCount?: number;
}

interface QueuedEvent {
  eventId: string;
  type: ClientEventType;
  path: string;
  referrer?: string;
  productId?: number;
  categoryId?: number;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  properties?: { query?: string; resultsCount?: number; variantId?: number; quantity?: number; itemCount?: number };
}

const ENDPOINT = "/api/v1/analytics/events";
const MAX_BATCH = 20;
const DEDUPE_WINDOW_MS = 2000;
const UTM_KEY = "utm.v1";

let queue: QueuedEvent[] = [];
let timer: ReturnType<typeof setTimeout> | null = null;
const recent = new Map<string, number>();

/** Captures campaign parameters once per visit, from the landing URL only. */
function campaign(isNewSession: boolean): Pick<QueuedEvent, "utmSource" | "utmMedium" | "utmCampaign"> {
  try {
    if (isNewSession) {
      const p = new URLSearchParams(window.location.search);
      const utm = { utmSource: p.get("utm_source") ?? undefined, utmMedium: p.get("utm_medium") ?? undefined, utmCampaign: p.get("utm_campaign") ?? undefined };
      window.sessionStorage.setItem(UTM_KEY, JSON.stringify(utm));
      return utm;
    }
    return JSON.parse(window.sessionStorage.getItem(UTM_KEY) ?? "{}");
  } catch {
    return {};
  }
}

export function track(type: ClientEventType, props: TrackProps = {}) {
  if (typeof window === "undefined" || !hasAnalyticsConsent()) {
    return;
  }
  const path = window.location.pathname; // never the query string
  // Guards against React Strict Mode double effects and component remounts.
  const key = `${type}|${path}|${props.productId ?? ""}|${props.categoryId ?? ""}|${props.query ?? ""}|${props.variantId ?? ""}`;
  const now = Date.now();
  if ((recent.get(key) ?? 0) > now - DEDUPE_WINDOW_MS) {
    return;
  }
  recent.set(key, now);

  const session = getSessionId();
  const properties = {
    query: props.query?.slice(0, 100),
    resultsCount: props.resultsCount,
    variantId: props.variantId,
    quantity: props.quantity,
    itemCount: props.itemCount,
  };
  queue.push({
    eventId: crypto.randomUUID(),
    type,
    path,
    referrer: type === "PAGE_VIEW" && session.isNew && document.referrer ? document.referrer : undefined,
    productId: props.productId,
    categoryId: props.categoryId,
    ...(type === "PAGE_VIEW" ? campaign(session.isNew) : {}),
    properties: Object.values(properties).some((v) => v !== undefined) ? properties : undefined,
  });
  if (queue.length >= MAX_BATCH) {
    flush();
  } else if (!timer) {
    timer = setTimeout(flush, 1500);
  }
}

export function flush(useBeacon = false) {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
  if (queue.length === 0 || !hasAnalyticsConsent()) {
    queue = [];
    return;
  }
  const events = queue.splice(0, MAX_BATCH);
  const body = JSON.stringify({ visitorId: getVisitorId(), sessionId: getSessionId().id, events });
  if (useBeacon && navigator.sendBeacon) {
    navigator.sendBeacon(ENDPOINT, new Blob([body], { type: "application/json" }));
  } else {
    void fetch(ENDPOINT, { method: "POST", body, headers: { "Content-Type": "application/json" }, keepalive: true, credentials: "same-origin" })
      .catch(() => undefined);
  }
  if (queue.length > 0) {
    flush(useBeacon);
  }
}

if (typeof window !== "undefined") {
  // Deliver queued events when the tab is hidden or closed.
  window.addEventListener("pagehide", () => flush(true));
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      flush(true);
    }
  });
}

/** Pages that are never tracked (must match the server-side exclusion list). */
export function isTrackablePath(path: string): boolean {
  return !/^\/(account|admin|login|register|forgot-password|reset-password|verify-email|checkout\/payment)(\/|$)/.test(path);
}
