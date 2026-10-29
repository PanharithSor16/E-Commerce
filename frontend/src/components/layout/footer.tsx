import Link from "next/link";

import { CookieSettingsLink } from "./cookie-settings-link";

const SITE_NAME = process.env.NEXT_PUBLIC_SITE_NAME ?? "Shop";

export function Footer() {
  return (
    <footer className="mt-16 border-t bg-muted/30">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-10 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div className="space-y-2">
          <p className="font-semibold">{SITE_NAME}</p>
          <p className="text-muted-foreground">Quality products, delivered fast.</p>
        </div>
        <div className="space-y-2">
          <p className="font-medium">Shop</p>
          <ul className="space-y-1 text-muted-foreground">
            <li><Link href="/products" className="hover:text-foreground">All products</Link></li>
            <li><Link href="/products?sort=newest" className="hover:text-foreground">New arrivals</Link></li>
            <li><Link href="/products?sort=popular" className="hover:text-foreground">Best sellers</Link></li>
          </ul>
        </div>
        <div className="space-y-2">
          <p className="font-medium">Contact</p>
          <ul className="space-y-1 text-muted-foreground">
            <li><a href="mailto:support@example.com" className="hover:text-foreground">support@example.com</a></li>
            <li>Mon–Fri, 9:00–17:00</li>
          </ul>
        </div>
        <div className="space-y-2">
          <p className="font-medium">Policies</p>
          <ul className="space-y-1 text-muted-foreground">
            <li><Link href="/privacy" className="hover:text-foreground">Privacy policy</Link></li>
            <li><Link href="/privacy#returns" className="hover:text-foreground">Shipping &amp; returns</Link></li>
            <li><CookieSettingsLink /></li>
          </ul>
        </div>
      </div>
      <div className="border-t py-4 text-center text-xs text-muted-foreground">
        © {new Date().getFullYear()} {SITE_NAME}. All rights reserved.
      </div>
    </footer>
  );
}
