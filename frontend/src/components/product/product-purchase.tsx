"use client";

import { Minus, Plus } from "lucide-react";
import { useMemo, useState } from "react";

import { useCart } from "@/components/providers/cart-provider";
import { Button } from "@/components/ui/button";
import type { ProductDetail } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import { cn } from "@/lib/utils";

import { ProductImage } from "./product-card";

export function ProductGallery({ product }: { product: ProductDetail }) {
  const images = product.images.length > 0 ? product.images : [];
  const [active, setActive] = useState(() => Math.max(0, images.findIndex((i) => i.primary)));
  const current = images[active];
  return (
    <div className="space-y-3">
      <div className="aspect-square overflow-hidden rounded-xl border">
        <ProductImage src={current?.url ?? null} alt={current?.altText ?? product.name} className="size-full" />
      </div>
      {images.length > 1 && (
        <div className="flex gap-2 overflow-x-auto">
          {images.map((img, i) => (
            <button key={img.id} type="button" onClick={() => setActive(i)} aria-label={`Show image ${i + 1}`}
                    className={cn("size-16 shrink-0 overflow-hidden rounded-md border-2", i === active ? "border-primary" : "border-transparent")}>
              <ProductImage src={img.url} alt="" className="size-full" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/** Size/color selection and add-to-cart. Price and stock shown here are for display; the server re-checks both. */
export function AddToCart({ product }: { product: ProductDetail }) {
  const { add } = useCart();
  const sizes = useMemo(() => unique(product.variants.map((v) => v.size)), [product.variants]);
  const colors = useMemo(() => unique(product.variants.map((v) => v.color)), [product.variants]);
  const firstInStock = product.variants.find((v) => v.inStock) ?? product.variants[0];
  const [size, setSize] = useState<string | null>(firstInStock?.size ?? null);
  const [color, setColor] = useState<string | null>(firstInStock?.color ?? null);
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);

  const variant = product.variants.find((v) => v.size === size && v.color === color)
    ?? (product.variants.length === 1 ? product.variants[0] : undefined);
  const maxQty = Math.max(1, Math.min(variant?.availableQuantity ?? 1, 99));

  return (
    <div className="space-y-5">
      <p className="text-2xl font-semibold">
        {formatMoney(variant?.price ?? product.price, product.currency)}
        {product.discountPrice && (variant?.price ?? product.price) === product.price && (
          <span className="ml-2 text-base text-muted-foreground line-through">{formatMoney(product.basePrice, product.currency)}</span>
        )}
      </p>
      {sizes.length > 0 && (
        <OptionGroup label="Size" options={sizes} value={size} onChange={setSize}
                     isAvailable={(s) => product.variants.some((v) => v.size === s && (color === null || v.color === color) && v.inStock)} />
      )}
      {colors.length > 0 && (
        <OptionGroup label="Color" options={colors} value={color} onChange={setColor}
                     isAvailable={(c) => product.variants.some((v) => v.color === c && (size === null || v.size === size) && v.inStock)} />
      )}
      <p className={cn("text-sm", variant?.inStock ? "text-emerald-600" : "text-destructive")} aria-live="polite">
        {!variant ? "This combination is not available" : !variant.inStock ? "Out of stock"
          : variant.lowStock ? `Only ${variant.availableQuantity} left` : "In stock"}
      </p>
      <div className="flex items-center gap-3">
        <div className="flex items-center rounded-lg border">
          <Button variant="ghost" size="icon" aria-label="Decrease quantity" disabled={quantity <= 1}
                  onClick={() => setQuantity((q) => Math.max(1, q - 1))}><Minus /></Button>
          <span className="w-10 text-center tabular-nums" aria-live="polite" aria-label="Quantity">{quantity}</span>
          <Button variant="ghost" size="icon" aria-label="Increase quantity" disabled={quantity >= maxQty}
                  onClick={() => setQuantity((q) => Math.min(maxQty, q + 1))}><Plus /></Button>
        </div>
        <Button size="lg" className="flex-1" disabled={!variant?.inStock || busy} onClick={async () => {
          if (!variant) {
            return;
          }
          setBusy(true);
          const ok = await add(variant.id, quantity, product.id);
          setBusy(false);
          if (ok) {
            setQuantity(1);
          }
        }}>
          {busy ? "Adding…" : "Add to cart"}
        </Button>
      </div>
      {variant && <p className="text-xs text-muted-foreground">SKU: {variant.sku}</p>}
    </div>
  );
}

function OptionGroup({ label, options, value, onChange, isAvailable }: {
  label: string; options: string[]; value: string | null; onChange: (v: string) => void; isAvailable: (v: string) => boolean;
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{label}{value ? `: ${value}` : ""}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((o) => (
          <button key={o} type="button" onClick={() => onChange(o)} aria-pressed={value === o}
                  className={cn("min-w-10 rounded-md border px-3 py-1.5 text-sm",
                    value === o ? "border-primary bg-primary text-primary-foreground" : "hover:bg-muted",
                    !isAvailable(o) && "text-muted-foreground line-through")}>
            {o}
          </button>
        ))}
      </div>
    </fieldset>
  );
}

function unique(values: (string | null)[]): string[] {
  return [...new Set(values.filter((v): v is string => !!v))];
}
