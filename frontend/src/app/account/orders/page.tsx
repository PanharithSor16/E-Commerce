import type { Metadata } from "next";

import { MyOrdersList } from "@/components/account/order-views";

export const metadata: Metadata = { title: "My orders" };

export default function OrdersPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">My orders</h1>
      <MyOrdersList />
    </div>
  );
}
