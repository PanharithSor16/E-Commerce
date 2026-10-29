"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { toast } from "sonner";

import { OrderDetailView, StatusBadge } from "@/components/account/order-views";
import { ConfirmationDialog } from "@/components/common/confirmation-dialog";
import { DataTable } from "@/components/common/data-table";
import { Field, NativeSelect } from "@/components/common/form";
import { ErrorState, LoadingState } from "@/components/common/states";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useApi } from "@/hooks/use-api";
import { errorMessage, newIdempotencyKey } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import type { OrderDetail, OrderStatus, PaymentStatus } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";

import { PageHeader } from "./admin-shell";

const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  CONFIRMED: "PROCESSING",
  PROCESSING: "SHIPPED",
  SHIPPED: "DELIVERED",
};

export function OrderList() {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [filters, setFilters] = useState<{ q?: string; status?: OrderStatus; paymentStatus?: PaymentStatus; from?: string; to?: string }>({});
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi(() => adminApi.orders({ ...filters, page }), [JSON.stringify(filters), page]);
  const set = (patch: Partial<typeof filters>) => { setPage(0); setFilters((f) => ({ ...f, ...patch })); };

  return (
    <div className="space-y-6">
      <PageHeader title="Orders" />
      <div className="flex flex-wrap items-end gap-2">
        <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); set({ q: q.trim() || undefined }); }}>
          <Input placeholder="Order number or email" aria-label="Search orders" value={q} onChange={(e) => setQ(e.target.value)} className="w-60" />
          <Button type="submit" variant="outline">Search</Button>
        </form>
        <NativeSelect aria-label="Fulfillment status" className="w-40" value={filters.status ?? ""} onChange={(e) => set({ status: (e.target.value || undefined) as OrderStatus })}>
          <option value="">All statuses</option>
          {["PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"].map((s) => <option key={s} value={s}>{s}</option>)}
        </NativeSelect>
        <NativeSelect aria-label="Payment status" className="w-44" value={filters.paymentStatus ?? ""} onChange={(e) => set({ paymentStatus: (e.target.value || undefined) as PaymentStatus })}>
          <option value="">All payments</option>
          {["PENDING", "PAID", "FAILED", "PARTIALLY_REFUNDED", "REFUNDED", "CANCELLED"].map((s) => <option key={s} value={s}>{s}</option>)}
        </NativeSelect>
        <input type="date" aria-label="From date" className="h-8 rounded-lg border bg-background px-2 text-sm" value={filters.from ?? ""} onChange={(e) => set({ from: e.target.value || undefined })} />
        <input type="date" aria-label="To date" className="h-8 rounded-lg border bg-background px-2 text-sm" value={filters.to ?? ""} onChange={(e) => set({ to: e.target.value || undefined })} />
      </div>
      <DataTable rows={data?.content} loading={loading} error={error} onRetry={reload} rowKey={(o) => o.id}
        onRowClick={(o) => router.push(`/admin/orders/${o.id}`)} emptyTitle="No orders match"
        page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements} onPageChange={setPage}
        columns={[
          { key: "n", header: "Order", cell: (o) => <span className="font-medium">{o.orderNumber}</span> },
          { key: "c", header: "Customer", cell: (o) => o.customerEmail, hideOnMobile: true },
          { key: "d", header: "Placed", cell: (o) => formatDate(o.placedAt), hideOnMobile: true },
          { key: "s", header: "Status", cell: (o) => <StatusBadge status={o.status} /> },
          { key: "p", header: "Payment", cell: (o) => <StatusBadge status={o.paymentStatus} /> },
          { key: "t", header: "Total", className: "text-right", cell: (o) => formatMoney(o.grandTotal, o.currency) },
        ]} />
    </div>
  );
}

export function AdminOrderDetail({ orderId }: { orderId: number }) {
  const { can } = useAuth();
  const { data: order, error, reload, setData } = useApi(() => adminApi.order(orderId), [orderId]);
  const [dialog, setDialog] = useState<"advance" | "cancel" | "refund" | null>(null);
  const [note, setNote] = useState("");
  const [amount, setAmount] = useState("");
  const refundKey = useRef(newIdempotencyKey());

  if (error) {
    return <ErrorState message={error} onRetry={reload} />;
  }
  if (!order) {
    return <LoadingState />;
  }
  const next = NEXT_STATUS[order.status];
  const paid = order.paymentStatus === "PAID" || order.paymentStatus === "PARTIALLY_REFUNDED";
  const refundable = (Number(order.grandTotal) - Number(order.refundedTotal)).toFixed(2);
  const cancellable = ["PENDING", "CONFIRMED", "PROCESSING"].includes(order.status);

  const act = async (fn: () => Promise<OrderDetail>, success: string) => {
    try {
      setData(await fn());
      setNote("");
      toast.success(success);
    } catch (e) {
      toast.error(errorMessage(e));
      throw e;
    }
  };

  return (
    <>
      <OrderDetailView order={order} actions={
        <>
          {next && paid && can("ORDER_FULFILL") && <Button size="sm" onClick={() => setDialog("advance")}>Mark as {next.toLowerCase()}</Button>}
          {paid && can("ORDER_REFUND") && Number(refundable) > 0 && <Button size="sm" variant="outline" onClick={() => { setAmount(refundable); setDialog("refund"); }}>Refund</Button>}
          {cancellable && can("ORDER_REFUND") && <Button size="sm" variant="destructive" onClick={() => setDialog("cancel")}>Cancel order</Button>}
        </>
      } />
      <ConfirmationDialog open={dialog === "advance"} onOpenChange={(o) => !o && setDialog(null)} title={`Mark order as ${next?.toLowerCase()}?`}
        confirmLabel="Update status" onConfirm={() => act(() => adminApi.updateOrderStatus(order.id, next!, note || undefined), "Status updated")}>
        <Textarea placeholder="Note (optional, e.g. tracking number)" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />
      </ConfirmationDialog>
      <ConfirmationDialog open={dialog === "cancel"} onOpenChange={(o) => !o && setDialog(null)} destructive confirmLabel="Cancel order"
        title={`Cancel ${order.orderNumber}?`}
        description={paid ? `The remaining ${formatMoney(refundable, order.currency)} will be refunded and stock restored.` : "Stock will be restored."}
        onConfirm={() => act(() => adminApi.cancelOrder(order.id, note || undefined), "Order cancelled")}>
        <Textarea placeholder="Reason" value={note} maxLength={255} onChange={(e) => setNote(e.target.value)} />
      </ConfirmationDialog>
      <ConfirmationDialog open={dialog === "refund"} onOpenChange={(o) => !o && setDialog(null)} confirmLabel="Issue refund"
        title="Refund payment" description={`Up to ${formatMoney(refundable, order.currency)} can be refunded to the original payment method.`}
        onConfirm={async () => {
          await act(() => adminApi.refundOrder(order.id, amount, note || undefined, refundKey.current), "Refund issued");
          refundKey.current = newIdempotencyKey();
        }}>
        <div className="space-y-3">
          <Field id="amount" label="Amount"><Input id="amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>
          <Field id="reason" label="Reason"><Input id="reason" value={note} maxLength={255} onChange={(e) => setNote(e.target.value)} /></Field>
        </div>
      </ConfirmationDialog>
    </>
  );
}
