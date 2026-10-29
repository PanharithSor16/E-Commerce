"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { cn } from "@/lib/utils";

const LINKS = [
  { href: "/account", label: "Overview" },
  { href: "/account/orders", label: "Orders" },
  { href: "/account/profile", label: "Profile & addresses" },
  { href: "/account/security", label: "Security" },
  { href: "/account/sessions", label: "Active sessions" },
];

export function AccountNav() {
  const pathname = usePathname();
  return (
    <nav aria-label="Account" className="flex gap-1 overflow-x-auto md:flex-col">
      {LINKS.map((l) => {
        const active = l.href === "/account" ? pathname === l.href : pathname.startsWith(l.href);
        return (
          <Link key={l.href} href={l.href} aria-current={active ? "page" : undefined}
                className={cn("shrink-0 rounded-md px-3 py-2 text-sm", active ? "bg-muted font-medium" : "text-muted-foreground hover:bg-muted/60")}>
            {l.label}
          </Link>
        );
      })}
    </nav>
  );
}
