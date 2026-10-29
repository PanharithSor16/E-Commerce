import type { Metadata } from "next";
import { Suspense } from "react";

import { PagerLinks, pagerProps } from "@/components/common/pager-links";
import { EmptyState, ErrorState } from "@/components/common/states";
import { ProductGrid } from "@/components/product/product-card";
import { ProductFilters } from "@/components/product/product-filters";
import { serverFetch } from "@/lib/api/server";
import type { Category, PageResponse, ProductSummary } from "@/lib/api/types";
import { catalogQuery, pageHref } from "@/lib/catalog-query";

export const metadata: Metadata = { title: "All products" };

export default async function ProductsPage({ searchParams }: PageProps<"/products">) {
  const sp = await searchParams;
  const [result, categories] = await Promise.all([
    serverFetch<PageResponse<ProductSummary>>(`/products?${catalogQuery(sp)}`).catch(() => null),
    serverFetch<Category[]>("/categories").catch(() => [] as Category[]),
  ]);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">All products</h1>
      <Suspense>
        <ProductFilters categories={categories} />
      </Suspense>
      {!result ? (
        <ErrorState message="Products could not be loaded." />
      ) : result.content.length === 0 ? (
        <EmptyState title="No products match your filters" description="Try removing a filter or widening the price range." />
      ) : (
        <>
          <ProductGrid products={result.content} />
          <PagerLinks {...pagerProps("/products", sp, result, pageHref)} />
        </>
      )}
    </div>
  );
}
