import type { Metadata } from "next";

import { CheckoutForm } from "@/components/checkout/checkout-form";
import { requireUser } from "@/lib/auth/session";

export const metadata: Metadata = { title: "Checkout" };

export default async function CheckoutPage() {
  await requireUser("/checkout");
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Checkout</h1>
      <CheckoutForm />
    </div>
  );
}
