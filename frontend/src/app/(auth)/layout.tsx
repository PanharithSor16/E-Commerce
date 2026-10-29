import { ShoppingBag } from "lucide-react";
import Link from "next/link";

const SITE_NAME = process.env.NEXT_PUBLIC_SITE_NAME ?? "Shop";

export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center bg-muted/30 px-4 py-10">
      <Link href="/" className="mb-6 flex items-center gap-2 text-lg font-semibold">
        <ShoppingBag className="size-5" aria-hidden /> {SITE_NAME}
      </Link>
      <div className="w-full max-w-md">{children}</div>
    </div>
  );
}
