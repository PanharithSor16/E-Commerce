"use client";

import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmationDialog } from "@/components/common/confirmation-dialog";
import { DataTable } from "@/components/common/data-table";
import { ErrorState, LoadingState } from "@/components/common/states";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { errorMessage } from "@/lib/api/client";
import { orderApi } from "@/lib/api/endpoints";
import type { OrderDetail, OrderStatus, PaymentStatus } from "@/lib/api/types";
import { useApi } from "@/hooks/use-api";
import { formatDate, formatDateTime, formatMoney, humanize } from "@/lib/format";

export function StatusBadge({ status }: { status: OrderStatus | PaymentStatus | string }) {
  const variant = ["PAID", "DELIVERED", "CONFIRMED", "SUCCESS", "SUCCEEDED"].includes(status) ? "default"
    : ["CANCELLED", "FAILED", "REFUNDED", "FAILURE"].includes(status) ? "destructive" : "secondary";
  return <Badge variant={variant}>{humanize(status)}</Badge>;
}

export function MyOrdersList() {
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi(() => orderApi.list(page), [page]);

  return (
    <DataTable
      rows={data?.content} loading={loading} error={error} onRetry={reload} rowKey={(o) => o.id}
      emptyTitle="No orders yet" emptyDescription="When you place an order it will show up here."
      page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements} onPageChange={setPage}
      columns={[
        { key: "number", header: "Order", cell: (o) => <Link href={`/account/orders/${o.id}`} className="font-medium underline-offset-4 hover:underline">{o.orderNumber}</Link> },
        { key: "date", header: "Date", cell: (o) => formatDate(o.placedAt), hideOnMobile: true },
        { key: "status", header: "Status", cell: (o) => <StatusBadge status={o.status} /> },
        { key: "payment", header: "Payment", cell: (o) => <StatusBadge status={o.paymentStatus} />, hideOnMobile: true },
        { key: "total", header: "Total", className: "text-right", cell: (o) => formatMoney(o.grandTotal, o.currency) },
      ]}
    />
  );
}

/** Order detail shared by the customer account and the admin order screen. */
export function OrderDetailView({ order, actions }: { order: OrderDetail; actions?: React.ReactNode }) {
  const shipping = order.addresses.find((a) => a.type === "SHIPPING");
  const billing = order.addresses.find((a) => a.type === "BILLING");
  const row = (label: string, value: string, negative = false) => (
    <div className="flex justify-between text-sm"><span className="text-muted-foreground">{label}</span><span>{negative ? "−" : ""}{formatMoney(value, order.currency)}</span></div>
  );
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Order {order.orderNumber}</h1>
          <p className="text-sm text-muted-foreground">Placed {formatDateTime(order.placedAt)}{order.customerEmail ? ` · ${order.customerName} (${order.customerEmail})` : ""}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={order.status} />
          <StatusBadge status={order.paymentStatus} />
          {actions}
        </div>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <Card>
          <CardHeader><CardTitle>Items</CardTitle></CardHeader>
          <CardContent>
            <ul className="divide-y">
              {order.items.map((i) => (
                <li key={i.id} className="flex justify-between gap-4 py-3 text-sm">
                  <div>
                    <p className="font-medium">{i.productName}</p>
                    <p className="text-muted-foreground">{[i.variantDescription, `SKU ${i.sku}`].filter(Boolean).join(" · ")}</p>
                    <p className="text-muted-foreground">{i.quantity} × {formatMoney(i.unitPrice, order.currency)}</p>
                  </div>
                  <p className="font-medium">{formatMoney(i.lineTotal, order.currency)}</p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle>Summary</CardTitle></CardHeader>
            <CardContent className="space-y-2">
              {row("Subtotal", order.subtotal)}
              {Number(order.discountTotal) > 0 && row(`Discount${order.couponCode ? ` (${order.couponCode})` : ""}`, order.discountTotal, true)}
              {row(`Shipping${order.shippingMethodName ? ` (${order.shippingMethodName})` : ""}`, order.shippingTotal)}
              {Number(order.taxTotal) > 0 && row("Tax", order.taxTotal)}
              <div className="flex justify-between border-t pt-2 font-semibold"><span>Total</span><span>{formatMoney(order.grandTotal, order.currency)}</span></div>
              {Number(order.refundedTotal) > 0 && row("Refunded", order.refundedTotal, true)}
            </CardContent>
          </Card>
          {shipping && (
            <Card>
              <CardHeader><CardTitle>Shipping to</CardTitle></CardHeader>
              <CardContent className="text-sm">
                <address className="not-italic">
                  {shipping.recipientName}<br />{shipping.line1}{shipping.line2 && <><br />{shipping.line2}</>}<br />
                  {shipping.city}{shipping.region ? `, ${shipping.region}` : ""} {shipping.postalCode}<br />{shipping.countryCode}
                </address>
                {billing && billing.line1 !== shipping.line1 && <p className="mt-3 text-muted-foreground">Billing: {billing.line1}, {billing.city}</p>}
              </CardContent>
            </Card>
          )}
        </div>
      </div>
      <Card>
        <CardHeader><CardTitle>History</CardTitle></CardHeader>
        <CardContent>
          <ol className="space-y-2 text-sm">
            {order.history.map((h, i) => (
              <li key={i} className="flex flex-wrap gap-x-3">
                <span className="w-40 text-muted-foreground">{formatDateTime(h.changedAt)}</span>
                <span>{h.statusType === "PAYMENT" ? "Payment" : "Order"}: {humanize(h.toStatus)}</span>
                {h.note && <span className="text-muted-foreground">— {h.note}</span>}
              </li>
            ))}
          </ol>
          {order.payments.length > 0 && (
            <div className="mt-4 space-y-1 border-t pt-4 text-sm">
              {order.payments.map((p) => (
                <p key={p.id} className="text-muted-foreground">
                  {p.provider} payment {humanize(p.status)} · {formatMoney(p.amount, p.currency)}
                  {p.cardBrand && ` · ${p.cardBrand.toUpperCase()} •••• ${p.cardLast4}`}{p.failureMessage && ` · ${p.failureMessage}`}
                </p>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

export function MyOrderDetail({ orderId }: { orderId: number }) {
  const { data: order, error, reload, setData: setOrder } = useApi(() => orderApi.get(orderId), [orderId]);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState("");

  if (error) {
    return <ErrorState message={error} onRetry={reload} />;
  }
  if (!order) {
    return <LoadingState />;
  }
  const needsPayment = order.status === "PENDING" && (order.paymentStatus === "PENDING" || order.paymentStatus === "FAILED");
  return (
    <>
      <OrderDetailView order={order} actions={
        <>
          {needsPayment && <Link href={`/checkout/payment/${order.id}`} className={buttonVariants({ size: "sm" })}>Pay now</Link>}
          {order.cancellable && <Button size="sm" variant="outline" onClick={() => setCancelOpen(true)}>Cancel order</Button>}
        </>
      } />
      <ConfirmationDialog open={cancelOpen} onOpenChange={setCancelOpen} destructive confirmLabel="Cancel order"
        title={`Cancel order ${order.orderNumber}?`}
        description={order.paymentStatus === "PAID" ? "The full amount will be refunded to your original payment method." : "This cannot be undone."}
        onConfirm={async () => {
          try {
            setOrder(await orderApi.cancel(order.id, reason || undefined));
            toast.success("Order cancelled");
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}>
        <Textarea placeholder="Reason (optional)" value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} />
      </ConfirmationDialog>
    </>
  );
}
