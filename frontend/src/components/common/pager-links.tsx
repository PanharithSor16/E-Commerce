import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";

import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Link-based pager for server-rendered lists. Hrefs are computed on the server
 * (functions cannot be passed to Client Components).
 */
export function PagerLinks({ page, totalPages, totalElements, prevHref, nextHref }: {
  page: number;
  totalPages: number;
  totalElements: number;
  prevHref: string | null;
  nextHref: string | null;
}) {
  const disabled = "pointer-events-none opacity-50";
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-4">
      <p className="text-sm text-muted-foreground">
        {totalPages > 1 ? `Page ${page + 1} of ${totalPages} · ` : ""}{totalElements} result(s)
      </p>
      {totalPages > 1 && (
        <div className="flex gap-2">
          <Link href={prevHref ?? "#"} aria-label="Previous page" aria-disabled={!prevHref}
                className={cn(buttonVariants({ variant: "outline", size: "sm" }), !prevHref && disabled)}>
            <ChevronLeft />
          </Link>
          <Link href={nextHref ?? "#"} aria-label="Next page" aria-disabled={!nextHref}
                className={cn(buttonVariants({ variant: "outline", size: "sm" }), !nextHref && disabled)}>
            <ChevronRight />
          </Link>
        </div>
      )}
    </nav>
  );
}

export function pagerProps(path: string, sp: Record<string, string | string[] | undefined>,
                           result: { page: number; totalPages: number; totalElements: number },
                           pageHref: (path: string, sp: Record<string, string | string[] | undefined>, page: number) => string) {
  return {
    page: result.page,
    totalPages: result.totalPages,
    totalElements: result.totalElements,
    prevHref: result.page > 0 ? pageHref(path, sp, result.page - 1) : null,
    nextHref: result.page < result.totalPages - 1 ? pageHref(path, sp, result.page + 1) : null,
  };
}
