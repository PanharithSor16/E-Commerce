"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { isTrackablePath, track } from "@/lib/analytics/track";

/**
 * Records a PAGE_VIEW for every App Router navigation (path only, never the query string).
 * The ref guards against double-firing from React Strict Mode and remounts; track() dedupes too.
 * Respects consent: track() is a no-op until analytics consent is granted.
 */
export function PageViewTracker() {
  const pathname = usePathname();
  const lastTracked = useRef<string | null>(null);
  const [consentVersion, setConsentVersion] = useState(0);

  useEffect(() => {
    const onConsent = () => {
      lastTracked.current = null; // record the current page once consent is granted
      setConsentVersion((v) => v + 1);
    };
    window.addEventListener("consent-changed", onConsent);
    return () => window.removeEventListener("consent-changed", onConsent);
  }, []);

  useEffect(() => {
    if (!pathname || lastTracked.current === pathname || !isTrackablePath(pathname)) {
      return;
    }
    lastTracked.current = pathname;
    track("PAGE_VIEW");
  }, [pathname, consentVersion]);

  return null;
}

/** Fires a single typed event when a server-rendered page mounts (product, category, search). */
export function TrackOnMount(props: {
  type: "PRODUCT_VIEW" | "CATEGORY_VIEW" | "SEARCH";
  productId?: number;
  categoryId?: number;
  query?: string;
  resultsCount?: number;
}) {
  const { type, productId, categoryId, query, resultsCount } = props;
  useEffect(() => {
    track(type, { productId, categoryId, query, resultsCount });
  }, [type, productId, categoryId, query, resultsCount]);
  return null;
}
