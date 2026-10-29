import type { Metadata } from "next";

import { CustomerList } from "@/components/admin/customers";

export const metadata: Metadata = { title: "Customers" };

export default function AdminCustomersPage() {
  return <CustomerList />;
}
