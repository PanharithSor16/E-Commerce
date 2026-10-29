import { ImageOff } from "lucide-react";
import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import type { ProductSummary } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import { cn } from "@/lib/utils";

/** Plain <img>: images are same-origin API media with immutable cache headers. */
export function ProductImage({ src, alt, className }: { src: string | null; alt: string; className?: string }) {
  if (!src) {
    return (
      <div className={cn("flex items-center justify-center bg-muted text-muted-foreground", className)}>
        <ImageOff className="size-8" aria-hidden />
        <span className="sr-only">No image</span>
      </div>
    );
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt={alt} loading="lazy" decoding="async" className={cn("object-cover", className)} />;
}

export function Price({ price, basePrice, discountPrice, currency, className }: {
  price: string; basePrice?: string; discountPrice?: string | null; currency: string; className?: string;
}) {
  return (
    <div className={cn("flex items-baseline gap-2", className)}>
      <span className="font-semibold">{formatMoney(price, currency)}</span>
      {discountPrice && basePrice && (
        <span className="text-sm text-muted-foreground line-through">{formatMoney(basePrice, currency)}</span>
      )}
    </div>
  );
}

export function ProductCard({ product }: { product: ProductSummary }) {
  const onSale = !!product.discountPrice;
  return (
    <Link href={`/products/${product.id}`}
          className="group flex flex-col overflow-hidden rounded-xl border bg-card transition hover:shadow-md focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">
      <div className="relative aspect-square overflow-hidden">
        <ProductImage src={product.imageUrl} alt={product.name} className="size-full transition group-hover:scale-105" />
        <div className="absolute top-2 left-2 flex gap-1">
          {onSale && <Badge variant="destructive">Sale</Badge>}
          {!product.inStock && <Badge variant="secondary">Sold out</Badge>}
        </div>
      </div>
      <div className="flex flex-1 flex-col gap-1 p-3">
        {product.brand && <p className="text-xs text-muted-foreground">{product.brand}</p>}
        <p className="line-clamp-2 text-sm font-medium">{product.name}</p>
        <Price className="mt-auto pt-1" price={product.price} basePrice={product.basePrice}
               discountPrice={product.discountPrice} currency={product.currency} />
      </div>
    </Link>
  );
}

export function ProductGrid({ products }: { products: ProductSummary[] }) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      {products.map((p) => <ProductCard key={p.id} product={p} />)}
    </div>
  );
}
