import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { saveConsent } from "./consent";
import { flush, isTrackablePath, track } from "./track";

describe("analytics tracker", () => {
  const fetchMock = vi.fn(() => Promise.resolve(new Response(null, { status: 202 })));

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockClear();
    window.history.pushState({}, "", "/products/42?token=secret&utm_source=news");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function sentBodies() {
    return fetchMock.mock.calls.map((call) => JSON.parse((call as unknown as [string, RequestInit])[1].body as string));
  }

  it("sends nothing and sets no visitor cookie without consent", () => {
    track("PAGE_VIEW");
    flush();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(document.cookie).not.toContain("vid=");
  });

  it("sends only the path (never the query string) once consent is granted", () => {
    saveConsent(true, false);
    expect(document.cookie).toContain("vid=");
    track("PRODUCT_VIEW", { productId: 42 });
    flush();
    const [body] = sentBodies();
    expect(body.events[0]).toMatchObject({ type: "PRODUCT_VIEW", path: "/products/42", productId: 42 });
    expect(JSON.stringify(body)).not.toContain("secret");
  });

  it("deduplicates rapid repeats (React Strict Mode double effects)", () => {
    saveConsent(true, false);
    track("PRODUCT_VIEW", { productId: 7 });
    track("PRODUCT_VIEW", { productId: 7 });
    flush();
    expect(sentBodies()[0].events).toHaveLength(1);
  });

  it("revoking consent removes the identifiers", () => {
    saveConsent(true, false);
    saveConsent(false, false);
    expect(document.cookie).not.toContain("vid=");
  });

  it("never tracks account, admin or authentication pages", () => {
    expect(isTrackablePath("/products/1")).toBe(true);
    expect(isTrackablePath("/account/orders")).toBe(false);
    expect(isTrackablePath("/admin")).toBe(false);
    expect(isTrackablePath("/reset-password")).toBe(false);
    expect(isTrackablePath("/checkout/payment/5")).toBe(false);
  });
});
