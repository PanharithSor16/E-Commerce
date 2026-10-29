"use client";

export function CookieSettingsLink() {
  return (
    <button type="button" className="hover:text-foreground" onClick={() => window.dispatchEvent(new Event("open-consent"))}>
      Cookie settings
    </button>
  );
}
