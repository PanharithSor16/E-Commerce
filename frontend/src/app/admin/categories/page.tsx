import type { Metadata } from "next";

import { CategoryManager } from "@/components/admin/catalog-settings";

export const metadata: Metadata = { title: "Categories" };

export default function AdminCategoriesPage() {
  return <CategoryManager />;
}
