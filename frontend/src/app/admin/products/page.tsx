import type { Metadata } from "next";

import { ProductList } from "@/components/admin/products";

export const metadata: Metadata = { title: "Products" };

export default function AdminProductsPage() {
  return <ProductList />;
}
