"use client";

import type { ReactNode } from "react";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

import { Pagination } from "./pagination";
import { EmptyState, ErrorState, LoadingState } from "./states";

export interface Column<T> {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  className?: string;
  /** Hide on narrow screens to keep tables usable on mobile. */
  hideOnMobile?: boolean;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string | number;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyDescription?: string;
  page?: number;
  totalPages?: number;
  totalElements?: number;
  onPageChange?: (page: number) => void;
  onRowClick?: (row: T) => void;
}

export function DataTable<T>({
  columns, rows, rowKey, loading, error, onRetry, emptyTitle = "Nothing here yet", emptyDescription,
  page, totalPages, totalElements, onPageChange, onRowClick,
}: DataTableProps<T>) {
  if (error) {
    return <ErrorState message={error} onRetry={onRetry} />;
  }
  if (loading && !rows) {
    return <LoadingState rows={6} />;
  }
  if (!rows || rows.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }
  return (
    <div className="space-y-4">
      <div className={cn("overflow-x-auto rounded-lg border", loading && "opacity-60")}>
        <Table>
          <TableHeader>
            <TableRow>
              {columns.map((c) => (
                <TableHead key={c.key} className={cn(c.className, c.hideOnMobile && "hidden md:table-cell")}>{c.header}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={rowKey(row)} onClick={onRowClick ? () => onRowClick(row) : undefined}
                        className={onRowClick ? "cursor-pointer" : undefined}>
                {columns.map((c) => (
                  <TableCell key={c.key} className={cn(c.className, c.hideOnMobile && "hidden md:table-cell")}>{c.cell(row)}</TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {page !== undefined && totalPages !== undefined && (
        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={onPageChange} />
      )}
    </div>
  );
}
