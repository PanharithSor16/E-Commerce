import type { Metadata } from "next";
import { Suspense } from "react";

import { TrackOnMount } from "@/components/analytics/page-view-tracker";
import { PagerLinks, pagerProps } from "@/components/common/pager-links";
import { SearchBar } from "@/components/common/search-bar";
import { EmptyState } from "@/components/common/states";
import { ProductGrid } from "@/components/product/product-card";
import { ProductFilters } from "@/components/product/product-filters";
import { serverFetch } from "@/lib/api/server";
import type { Category, PageResponse, ProductSummary } from "@/lib/api/types";
import { catalogQuery, pageHref } from "@/lib/catalog-query";

export const metadata: Metadata = { title: "Search" };

export default async function SearchPage({ searchParams }: PageProps<"/search">) {
  const sp = await searchParams;
  const q = (Array.isArray(sp.q) ? sp.q[0] : sp.q)?.trim().slice(0, 100) ?? "";
  const [result, categories] = await Promise.all([
    q ? serverFetch<PageResponse<ProductSummary>>(`/products?${catalogQuery(sp)}`).catch(() => null) : null,
    serverFetch<Category[]>("/categories").catch(() => [] as Category[]),
  ]);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{q ? <>Results for “{q}”</> : "Search"}</h1>
      <SearchBar initialQuery={q} className="max-w-xl" />
      {q && result && <TrackOnMount type="SEARCH" query={q} resultsCount={result.totalElements} />}
      {q && (
        <Suspense>
          <ProductFilters categories={categories} />
        </Suspense>
      )}
      {!q ? (
        <EmptyState title="What are you looking for?" description="Search by product name, brand or SKU." />
      ) : !result || result.content.length === 0 ? (
        <EmptyState title="No products found" description="Check the spelling or try a more general term." />
      ) : (
        <>
          <ProductGrid products={result.content} />
          <PagerLinks {...pagerProps("/search", sp, result, pageHref)} />
        </>
      )}
    </div>
  );
}
