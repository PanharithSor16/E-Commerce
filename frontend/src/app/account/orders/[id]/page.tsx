import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { MyOrderDetail } from "@/components/account/order-views";

export const metadata: Metadata = { title: "Order details" };

export default async function OrderPage({ params }: PageProps<"/account/orders/[id]">) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) {
    notFound();
  }
  return <MyOrderDetail orderId={Number(id)} />;
}
