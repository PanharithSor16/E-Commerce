import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { ProductEditor } from "@/components/admin/products";

export const metadata: Metadata = { title: "Edit product" };

export default async function EditProductPage({ params }: PageProps<"/admin/products/[id]">) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) {
    notFound();
  }
  return <ProductEditor productId={Number(id)} />;
}
