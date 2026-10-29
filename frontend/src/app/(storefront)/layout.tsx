import { Footer } from "@/components/layout/footer";
import { Header } from "@/components/layout/header";
import { serverFetch } from "@/lib/api/server";
import type { Category } from "@/lib/api/types";

export default async function StorefrontLayout({ children }: LayoutProps<"/">) {
  const categories = await serverFetch<Category[]>("/categories").catch(() => []);
  return (
    <>
      <Header categories={categories} />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6">{children}</main>
      <Footer />
    </>
  );
}
