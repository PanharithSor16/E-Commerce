import type { Metadata } from "next";

import { OrderList } from "@/components/admin/orders";

export const metadata: Metadata = { title: "Orders" };

export default function AdminOrdersPage() {
  return <OrderList />;
}
