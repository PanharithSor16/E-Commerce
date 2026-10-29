"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";

interface PaginationProps {
  page: number; // zero-based
  totalPages: number;
  totalElements?: number;
  /** Callback-based paging for client lists (server lists use PagerLinks). */
  onPageChange?: (page: number) => void;
}

export function Pagination({ page, totalPages, totalElements, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return totalElements !== undefined ? <p className="text-sm text-muted-foreground">{totalElements} result(s)</p> : null;
  }
  const prev = page > 0 ? page - 1 : null;
  const next = page < totalPages - 1 ? page + 1 : null;

  const control = (target: number | null, label: string, icon: React.ReactNode) => {
    if (target === null) {
      return <Button variant="outline" size="sm" disabled aria-label={label}>{icon}</Button>;
    }
    return <Button variant="outline" size="sm" aria-label={label} onClick={() => onPageChange?.(target)}>{icon}</Button>;
  };

  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-4">
      <p className="text-sm text-muted-foreground">
        Page {page + 1} of {totalPages}{totalElements !== undefined ? ` · ${totalElements} result(s)` : ""}
      </p>
      <div className="flex gap-2">
        {control(prev, "Previous page", <ChevronLeft />)}
        {control(next, "Next page", <ChevronRight />)}
      </div>
    </nav>
  );
}
