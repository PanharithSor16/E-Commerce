"use client";

import Link from "next/link";
import { useEffect, useState, useSyncExternalStore } from "react";

import { Button } from "@/components/ui/button";
import { api } from "@/lib/api/client";
import { getConsent, getVisitorId, saveConsent } from "@/lib/analytics/consent";

function subscribe(onChange: () => void) {
  window.addEventListener("consent-changed", onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener("consent-changed", onChange);
    window.removeEventListener("storage", onChange);
  };
}

/** Asks for analytics consent. Nothing is tracked and no visitor id exists until "Accept". */
export function ConsentBanner() {
  // Undecided visitors see the banner; the server snapshot is "decided" so nothing flashes during SSR.
  const undecided = useSyncExternalStore(subscribe, () => getConsent() === null, () => false);
  const [reopened, setReopened] = useState(false);
  const open = undecided || reopened;
  const [details, setDetails] = useState(false);
  const [analytics, setAnalytics] = useState(true);
  const [marketing, setMarketing] = useState(false);

  useEffect(() => {
    const reopen = () => setReopened(true);
    window.addEventListener("open-consent", reopen);
    return () => window.removeEventListener("open-consent", reopen);
  }, []);

  if (!open) {
    return null;
  }

  async function decide(a: boolean, m: boolean) {
    saveConsent(a, m);
    setReopened(false);
    try {
      await api.post("/consent", { visitorId: a ? getVisitorId() : null, analytics: a, marketing: m });
    } catch {
      // The local choice still applies; recording it server-side is best effort.
    }
  }

  return (
    <div role="dialog" aria-live="polite" aria-label="Cookie preferences"
         className="fixed inset-x-0 bottom-0 z-50 border-t bg-background/95 p-4 shadow-lg backdrop-blur">
      <div className="mx-auto flex max-w-5xl flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div className="space-y-2 text-sm">
          <p className="font-medium">We value your privacy</p>
          <p className="text-muted-foreground">
            Essential cookies keep you signed in and remember your cart. With your permission we also use
            privacy-friendly analytics (no advertising, no cross-site tracking) to improve the store.{" "}
            <Link href="/privacy" className="underline">Privacy policy</Link>
          </p>
          {details && (
            <div className="space-y-2 pt-1">
              <label className="flex items-center gap-2">
                <input type="checkbox" checked disabled className="size-4" /> Essential (always on)
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" className="size-4" checked={analytics} onChange={(e) => setAnalytics(e.target.checked)} />
                Analytics
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" className="size-4" checked={marketing} onChange={(e) => setMarketing(e.target.checked)} />
                Marketing emails personalization
              </label>
            </div>
          )}
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          {details ? (
            <Button onClick={() => decide(analytics, marketing)}>Save choices</Button>
          ) : (
            <Button variant="outline" onClick={() => setDetails(true)}>Customize</Button>
          )}
          <Button variant="outline" onClick={() => decide(false, false)}>Reject</Button>
          <Button onClick={() => decide(true, marketing)}>Accept analytics</Button>
        </div>
      </div>
    </div>
  );
}
