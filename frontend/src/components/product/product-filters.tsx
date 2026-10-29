"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

import { NativeSelect } from "@/components/common/form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Category } from "@/lib/api/types";

/** Filter/sort state lives in the URL so results are shareable and server-rendered. */
export function ProductFilters({ categories, showCategory = true }: { categories: Category[]; showCategory?: boolean }) {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const [minPrice, setMinPrice] = useState(params.get("minPrice") ?? "");
  const [maxPrice, setMaxPrice] = useState(params.get("maxPrice") ?? "");

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params.toString());
    for (const [k, v] of Object.entries(changes)) {
      if (v) {
        next.set(k, v);
      } else {
        next.delete(k);
      }
    }
    next.delete("page"); // filters reset paging
    router.push(`${pathname}?${next.toString()}`);
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      {showCategory && (
        <div className="w-44">
          <label htmlFor="filter-category" className="mb-1 block text-xs text-muted-foreground">Category</label>
          <NativeSelect id="filter-category" value={params.get("categoryId") ?? ""}
                        onChange={(e) => update({ categoryId: e.target.value || null })}>
            <option value="">All categories</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </NativeSelect>
        </div>
      )}
      <form className="flex items-end gap-2" onSubmit={(e) => {
        e.preventDefault();
        update({ minPrice: minPrice || null, maxPrice: maxPrice || null });
      }}>
        <div className="w-24">
          <label htmlFor="filter-min" className="mb-1 block text-xs text-muted-foreground">Min price</label>
          <Input id="filter-min" inputMode="decimal" type="number" min={0} step="0.01" value={minPrice}
                 onChange={(e) => setMinPrice(e.target.value)} />
        </div>
        <div className="w-24">
          <label htmlFor="filter-max" className="mb-1 block text-xs text-muted-foreground">Max price</label>
          <Input id="filter-max" inputMode="decimal" type="number" min={0} step="0.01" value={maxPrice}
                 onChange={(e) => setMaxPrice(e.target.value)} />
        </div>
        <Button type="submit" variant="outline" size="sm">Apply</Button>
      </form>
      <div className="ml-auto w-44">
        <label htmlFor="filter-sort" className="mb-1 block text-xs text-muted-foreground">Sort by</label>
        <NativeSelect id="filter-sort" value={params.get("sort") ?? "newest"} onChange={(e) => update({ sort: e.target.value })}>
          <option value="newest">Newest</option>
          <option value="popular">Most popular</option>
          <option value="price_asc">Price: low to high</option>
          <option value="price_desc">Price: high to low</option>
          <option value="name">Name</option>
        </NativeSelect>
      </div>
    </div>
  );
}
