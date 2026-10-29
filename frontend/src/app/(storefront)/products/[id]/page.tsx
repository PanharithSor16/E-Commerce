import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { TrackOnMount } from "@/components/analytics/page-view-tracker";
import { AddToCart, ProductGallery } from "@/components/product/product-purchase";
import { Badge } from "@/components/ui/badge";
import { serverFetchOrNull } from "@/lib/api/server";
import type { ProductDetail } from "@/lib/api/types";

async function load(id: string): Promise<ProductDetail | null> {
  if (!/^\d{1,18}$/.test(id)) {
    return null;
  }
  return serverFetchOrNull<ProductDetail>(`/products/${id}`);
}

export async function generateMetadata({ params }: PageProps<"/products/[id]">): Promise<Metadata> {
  const product = await load((await params).id).catch(() => null);
  return product ? { title: product.name, description: product.shortDescription ?? undefined } : { title: "Product not found" };
}

export default async function ProductPage({ params }: PageProps<"/products/[id]">) {
  const product = await load((await params).id);
  if (!product) {
    notFound();
  }

  return (
    <div className="space-y-8">
      <TrackOnMount type="PRODUCT_VIEW" productId={product.id} />
      <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
        <Link href="/products" className="hover:text-foreground">Products</Link>
        <span className="mx-2">/</span>
        <Link href={`/categories/${product.category.id}`} className="hover:text-foreground">{product.category.name}</Link>
      </nav>
      <div className="grid gap-8 md:grid-cols-2">
        <ProductGallery product={product} />
        <div className="space-y-4">
          {product.brand && <p className="text-sm text-muted-foreground">{product.brand}</p>}
          <h1 className="text-3xl font-semibold">{product.name}</h1>
          {product.discountPrice && <Badge variant="destructive">On sale</Badge>}
          {product.shortDescription && <p className="text-muted-foreground">{product.shortDescription}</p>}
          <AddToCart product={product} />
        </div>
      </div>
      {product.description && (
        <section className="max-w-3xl space-y-2">
          <h2 className="text-lg font-semibold">Description</h2>
          {/* Rendered as text (React escapes it); the backend also strips markup on write. */}
          <p className="whitespace-pre-line text-muted-foreground">{product.description}</p>
        </section>
      )}
    </div>
  );
}
