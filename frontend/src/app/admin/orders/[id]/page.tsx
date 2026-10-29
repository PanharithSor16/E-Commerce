import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { AdminOrderDetail } from "@/components/admin/orders";

export const metadata: Metadata = { title: "Order" };

export default async function AdminOrderPage({ params }: PageProps<"/admin/orders/[id]">) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) {
    notFound();
  }
  return <AdminOrderDetail orderId={Number(id)} />;
}
