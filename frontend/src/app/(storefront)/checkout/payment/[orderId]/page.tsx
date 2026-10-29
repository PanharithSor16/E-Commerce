import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { PaymentPanel } from "@/components/checkout/payment-panel";
import { LoadingState } from "@/components/common/states";
import { requireUser } from "@/lib/auth/session";

export const metadata: Metadata = { title: "Payment" };

export default async function PaymentPage({ params }: PageProps<"/checkout/payment/[orderId]">) {
  const { orderId } = await params;
  await requireUser(`/checkout/payment/${orderId}`);
  if (!/^\d{1,18}$/.test(orderId)) {
    notFound();
  }
  return (
    <div className="space-y-6">
      <h1 className="text-center text-2xl font-semibold">Complete your order</h1>
      <Suspense fallback={<LoadingState />}>
        <PaymentPanel orderId={Number(orderId)} />
      </Suspense>
    </div>
  );
}
