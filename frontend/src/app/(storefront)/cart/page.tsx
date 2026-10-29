import type { Metadata } from "next";

import { ShoppingCart } from "@/components/cart/shopping-cart";

export const metadata: Metadata = { title: "Cart" };

export default function CartPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Shopping cart</h1>
      <ShoppingCart />
    </div>
  );
}
