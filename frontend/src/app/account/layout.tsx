import { Footer } from "@/components/layout/footer";
import { Header } from "@/components/layout/header";
import { AccountNav } from "@/components/account/account-nav";
import { serverFetch } from "@/lib/api/server";
import type { Category } from "@/lib/api/types";
import { requireUser } from "@/lib/auth/session";

/** Server-side gate: unauthenticated visitors are redirected before any account UI renders. */
export default async function AccountLayout({ children }: LayoutProps<"/account">) {
  await requireUser("/account");
  const categories = await serverFetch<Category[]>("/categories").catch(() => []);
  return (
    <>
      <Header categories={categories} />
      <main className="mx-auto grid w-full max-w-7xl flex-1 gap-8 px-4 py-6 md:grid-cols-[200px_1fr]">
        <AccountNav />
        <div className="min-w-0">{children}</div>
      </main>
      <Footer />
    </>
  );
}
