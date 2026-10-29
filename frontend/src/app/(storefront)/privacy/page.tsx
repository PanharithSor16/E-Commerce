import type { Metadata } from "next";

import { CookieSettingsLink } from "@/components/layout/cookie-settings-link";

export const metadata: Metadata = { title: "Privacy policy" };

export default function PrivacyPage() {
  return (
    <article className="mx-auto max-w-3xl space-y-6">
      <header>
        <h1 className="text-3xl font-semibold">Privacy policy</h1>
        <p className="text-sm text-muted-foreground">Version 1.0</p>
      </header>

      <section className="space-y-2">
        <h2 className="text-xl font-semibold">What we collect</h2>
        <ul className="list-disc space-y-1 pl-6 text-muted-foreground">
          <li><strong>Account data</strong>: name, email, phone (optional) and addresses you save.</li>
          <li><strong>Orders</strong>: items, amounts and shipping details, kept as required by tax law.</li>
          <li><strong>Payments</strong>: handled by our payment provider. We never see or store your card number;
            we keep only the card brand and last four digits for your receipts.</li>
          <li><strong>Security logs</strong>: sign-in attempts with time, IP address and browser type, kept for
            12 months to protect your account against misuse.</li>
        </ul>
      </section>

      <section className="space-y-2">
        <h2 className="text-xl font-semibold">Cookies</h2>
        <p className="text-muted-foreground">
          <strong>Essential cookies</strong> keep you signed in (an HTTP-only session cookie), protect forms against
          cross-site request forgery, and remember your cart. They are always on.
        </p>
        <p className="text-muted-foreground">
          <strong>Analytics cookies</strong> are only set if you agree. They contain a random identifier (no name or
          email) that lets us count visits and understand which products are popular. We record pages viewed
          (without URL parameters), products viewed, cart actions and the source of a visit (for example a search
          engine or newsletter). Analytics data is not sold or shared with advertisers and raw events are deleted
          after 13 months; only aggregated daily statistics are kept longer.
        </p>
        <p className="text-muted-foreground">You can change your choice at any time: <CookieSettingsLink />.</p>
      </section>

      <section className="space-y-2">
        <h2 className="text-xl font-semibold">Your rights</h2>
        <p className="text-muted-foreground">
          You can view and update your profile, see and end your active sessions, and change your consent in your
          account. To request a copy or deletion of your data, contact support@example.com.
        </p>
      </section>

      <section id="returns" className="space-y-2">
        <h2 className="text-xl font-semibold">Shipping &amp; returns</h2>
        <p className="text-muted-foreground">
          Standard shipping takes 3–7 business days and is free for orders over $75. Unused items can be returned
          within 30 days for a refund to the original payment method.
        </p>
      </section>
    </article>
  );
}
