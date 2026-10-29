import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { CustomerDetailView } from "@/components/admin/customers";

export const metadata: Metadata = { title: "Customer" };

export default async function AdminCustomerPage({ params }: PageProps<"/admin/customers/[id]">) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) {
    notFound();
  }
  return <CustomerDetailView userId={Number(id)} />;
}
