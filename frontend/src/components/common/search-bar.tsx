"use client";

import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export function SearchBar({ initialQuery = "", className }: { initialQuery?: string; className?: string }) {
  const router = useRouter();
  const [q, setQ] = useState(initialQuery);

  return (
    <form role="search" className={cn("relative w-full", className)} onSubmit={(e) => {
      e.preventDefault();
      const term = q.trim();
      router.push(term ? `/search?q=${encodeURIComponent(term)}` : "/products");
    }}>
      <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search products…"
             aria-label="Search products" maxLength={100} className="pl-8" />
    </form>
  );
}
