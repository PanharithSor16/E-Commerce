"use client";

import { Minus, Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { EmptyState, LoadingState } from "@/components/common/states";
import { ProductImage } from "@/components/product/product-card";
import { useAuth } from "@/components/providers/auth-provider";
import { useCart } from "@/components/providers/cart-provider";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import type { Cart } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";

export function ShoppingCart() {
  const { cart, loading, update, remove } = useCart();
  const { user } = useAuth();

  if (loading && !cart) {
    return <LoadingState label="Loading your cart…" />;
  }
  if (!cart || cart.items.length === 0) {
    return (
      <EmptyState title="Your cart is empty" description="Browse the catalog and add something you like."
                  action={<Link href="/products" className={buttonVariants()}>Continue shopping</Link>} />
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_360px]">
      <section aria-label="Cart items" className="space-y-4">
        {cart.warnings.map((w) => (
          <Alert key={w} variant="destructive"><AlertDescription>{w}</AlertDescription></Alert>
        ))}
        <ul className="divide-y rounded-xl border">
          {cart.items.map((item) => (
            <li key={item.id} className="flex gap-4 p-4">
              <Link href={`/products/${item.productId}`} className="size-20 shrink-0 overflow-hidden rounded-md border">
                <ProductImage src={item.imageUrl} alt={item.productName} className="size-full" />
              </Link>
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <Link href={`/products/${item.productId}`} className="truncate font-medium hover:underline">{item.productName}</Link>
                {item.variantDescription && <p className="text-sm text-muted-foreground">{item.variantDescription}</p>}
                <p className="text-sm">{formatMoney(item.unitPrice, cart.currency)} each</p>
                {!item.available && <p className="text-sm text-destructive">No longer available</p>}
                <div className="mt-auto flex items-center gap-2">
                  <div className="flex items-center rounded-md border">
                    <Button variant="ghost" size="icon-sm" aria-label={`Decrease quantity of ${item.productName}`}
                            disabled={item.quantity <= 1} onClick={() => update(item.id, item.quantity - 1)}><Minus /></Button>
                    <span className="w-8 text-center text-sm tabular-nums">{item.quantity}</span>
                    <Button variant="ghost" size="icon-sm" aria-label={`Increase quantity of ${item.productName}`}
                            disabled={item.quantity >= item.availableQuantity} onClick={() => update(item.id, item.quantity + 1)}><Plus /></Button>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => remove(item.id)} aria-label={`Remove ${item.productName}`}>
                    <Trash2 /> <span className="hidden sm:inline">Remove</span>
                  </Button>
                </div>
              </div>
              <p className="shrink-0 font-medium">{formatMoney(item.lineTotal, cart.currency)}</p>
            </li>
          ))}
        </ul>
      </section>
      <aside className="space-y-4">
        <CartSummary cart={cart} />
        <CouponForm />
        {user ? (
          <Link href="/checkout" className={buttonVariants({ size: "lg", className: "w-full" })}>Proceed to checkout</Link>
        ) : (
          <div className="space-y-2">
            <Link href="/login?next=/checkout" className={buttonVariants({ size: "lg", className: "w-full" })}>Sign in to check out</Link>
            <p className="text-center text-xs text-muted-foreground">Your cart is kept when you sign in.</p>
          </div>
        )}
      </aside>
    </div>
  );
}

export function CartSummary({ cart, shippingLabel = "Shipping (estimate)" }: { cart: Pick<Cart, "subtotal" | "discountTotal" | "shippingEstimate" | "taxTotal" | "total" | "currency" | "couponCode">; shippingLabel?: string }) {
  const row = (label: string, value: string, negative = false) => (
    <div className="flex justify-between text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span>{negative ? "−" : ""}{formatMoney(value, cart.currency)}</span>
    </div>
  );
  return (
    <Card>
      <CardHeader><CardTitle>Order summary</CardTitle></CardHeader>
      <CardContent className="space-y-2">
        {row("Subtotal", cart.subtotal)}
        {Number(cart.discountTotal) > 0 && row(`Discount${cart.couponCode ? ` (${cart.couponCode})` : ""}`, cart.discountTotal, true)}
        {row(shippingLabel, cart.shippingEstimate)}
        {Number(cart.taxTotal) > 0 && row("Tax", cart.taxTotal)}
        <Separator />
      </CardContent>
      <CardFooter className="flex justify-between font-semibold">
        <span>Total</span>
        <span>{formatMoney(cart.total, cart.currency)}</span>
      </CardFooter>
    </Card>
  );
}

function CouponForm() {
  const { cart, applyCoupon, removeCoupon } = useCart();
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);

  if (cart?.couponCode) {
    return (
      <div className="flex items-center justify-between rounded-lg border px-3 py-2 text-sm">
        <span>Coupon <strong>{cart.couponCode}</strong> applied</span>
        <Button variant="ghost" size="sm" onClick={() => removeCoupon()}>Remove</Button>
      </div>
    );
  }
  return (
    <form className="flex gap-2" onSubmit={async (e) => {
      e.preventDefault();
      if (!code.trim()) {
        return;
      }
      setBusy(true);
      if (await applyCoupon(code.trim())) {
        setCode("");
      }
      setBusy(false);
    }}>
      <Input placeholder="Coupon code" aria-label="Coupon code" value={code} maxLength={40}
             onChange={(e) => setCode(e.target.value.toUpperCase())} />
      <Button type="submit" variant="outline" disabled={busy || !code.trim()}>Apply</Button>
    </form>
  );
}
