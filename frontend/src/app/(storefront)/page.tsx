import { ArrowRight } from "lucide-react";
import Link from "next/link";

import { SearchBar } from "@/components/common/search-bar";
import { EmptyState } from "@/components/common/states";
import { ProductGrid } from "@/components/product/product-card";
import { buttonVariants } from "@/components/ui/button";
import { serverFetch } from "@/lib/api/server";
import type { HomeData, ProductSummary } from "@/lib/api/types";

export default async function HomePage() {
  const home = await serverFetch<HomeData>("/products/home").catch(() => null);

  return (
    <div className="space-y-12">
      <section className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-zinc-900 to-zinc-700 px-6 py-14 text-white sm:px-12">
        <p className="text-sm tracking-widest text-zinc-300 uppercase">New season</p>
        <h1 className="mt-2 max-w-xl text-3xl font-bold sm:text-5xl">Everything you need, delivered fast.</h1>
        <p className="mt-3 max-w-lg text-zinc-300">Free standard shipping on orders over $75. Easy 30-day returns.</p>
        <div className="mt-6 flex max-w-md flex-col gap-3 sm:flex-row">
          <Link href="/products" className={buttonVariants({ size: "lg", variant: "secondary" })}>
            Shop now <ArrowRight />
          </Link>
        </div>
        <div className="mt-6 max-w-md text-foreground"><SearchBar /></div>
      </section>

      {!home ? (
        <EmptyState title="The store is temporarily unavailable" description="Please try again in a moment." />
      ) : (
        <>
          {home.categories.length > 0 && (
            <section aria-labelledby="categories-heading" className="space-y-4">
              <h2 id="categories-heading" className="text-xl font-semibold">Shop by category</h2>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                {home.categories.filter((c) => c.parentId === null).map((c) => (
                  <Link key={c.id} href={`/categories/${c.id}`}
                        className="rounded-xl border bg-card p-4 text-center text-sm font-medium transition hover:bg-muted">
                    {c.name}
                  </Link>
                ))}
              </div>
            </section>
          )}
          <Section title="Featured" products={home.featured} href="/products" />
          <Section title="New arrivals" products={home.newArrivals} href="/products?sort=newest" />
          <Section title="Best sellers" products={home.bestSellers} href="/products?sort=popular" />
        </>
      )}
    </div>
  );
}

function Section({ title, products, href }: { title: string; products: ProductSummary[]; href: string }) {
  if (products.length === 0) {
    return null;
  }
  return (
    <section className="space-y-4">
      <div className="flex items-end justify-between">
        <h2 className="text-xl font-semibold">{title}</h2>
        <Link href={href} className="text-sm text-muted-foreground hover:text-foreground">View all</Link>
      </div>
      <ProductGrid products={products} />
    </section>
  );
}
