import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { TrackOnMount } from "@/components/analytics/page-view-tracker";
import { PagerLinks, pagerProps } from "@/components/common/pager-links";
import { EmptyState } from "@/components/common/states";
import { ProductGrid } from "@/components/product/product-card";
import { ProductFilters } from "@/components/product/product-filters";
import { serverFetch, serverFetchOrNull } from "@/lib/api/server";
import type { Category, PageResponse, ProductSummary } from "@/lib/api/types";
import { catalogQuery, pageHref } from "@/lib/catalog-query";

async function loadCategory(id: string) {
  return /^\d{1,18}$/.test(id) ? serverFetchOrNull<Category>(`/categories/${id}`) : null;
}

export async function generateMetadata({ params }: PageProps<"/categories/[id]">): Promise<Metadata> {
  const category = await loadCategory((await params).id).catch(() => null);
  return { title: category?.name ?? "Category" };
}

export default async function CategoryPage({ params, searchParams }: PageProps<"/categories/[id]">) {
  const { id } = await params;
  const sp = await searchParams;
  const category = await loadCategory(id);
  if (!category) {
    notFound();
  }
  const [result, categories] = await Promise.all([
    serverFetch<PageResponse<ProductSummary>>(`/categories/${id}/products?${catalogQuery(sp)}`),
    serverFetch<Category[]>("/categories").catch(() => [] as Category[]),
  ]);

  return (
    <div className="space-y-6">
      <TrackOnMount type="CATEGORY_VIEW" categoryId={category.id} />
      <div>
        <h1 className="text-2xl font-semibold">{category.name}</h1>
        {category.description && <p className="mt-1 text-muted-foreground">{category.description}</p>}
      </div>
      <Suspense>
        <ProductFilters categories={categories} showCategory={false} />
      </Suspense>
      {result.content.length === 0 ? (
        <EmptyState title="No products in this category yet" />
      ) : (
        <>
          <ProductGrid products={result.content} />
          <PagerLinks {...pagerProps(`/categories/${id}`, sp, result, pageHref)} />
        </>
      )}
    </div>
  );
}
