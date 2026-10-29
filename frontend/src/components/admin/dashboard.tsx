"use client";

import { AlertTriangle } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { StatusBadge } from "@/components/account/order-views";
import { AnalyticsCards, BarList, FunnelChart, KpiCard, TrendChart } from "@/components/analytics/analytics-charts";
import { DataTable } from "@/components/common/data-table";
import { DateRangePicker } from "@/components/common/date-range-picker";
import { ErrorState, LoadingState } from "@/components/common/states";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useApi } from "@/hooks/use-api";
import { reportsApi } from "@/lib/api/endpoints";
import type { DateRangeQuery } from "@/lib/api/types";
import { formatDate, formatMoney, formatNumber, formatPercent } from "@/lib/format";

import { PageHeader } from "./admin-shell";

export function Dashboard() {
  const [range, setRange] = useState<DateRangeQuery>({ range: "30d" });
  const { data, error, loading, reload } = useApi(() => reportsApi.overview(range), [range.range, range.from, range.to]);

  return (
    <div className="space-y-6">
      <PageHeader title="Dashboard" description="Revenue figures come from orders, payments and refunds; traffic from consented analytics."
                  actions={<DateRangePicker value={range} onChange={setRange} />} />
      {error ? <ErrorState message={error} onRetry={reload} /> : !data ? <LoadingState /> : (
        <div className={loading ? "space-y-6 opacity-60" : "space-y-6"}>
          <AnalyticsCards>
            <KpiCard label="Paid revenue" value={formatMoney(data.current.money.paidRevenue)}
                     current={Number(data.current.money.paidRevenue)} previous={Number(data.previous.money.paidRevenue)} />
            <KpiCard label="Orders placed" value={formatNumber(data.current.ordersPlaced)}
                     current={data.current.ordersPlaced} previous={data.previous.ordersPlaced} />
            <KpiCard label="Unique visitors" value={formatNumber(data.current.uniqueVisitors)}
                     current={data.current.uniqueVisitors} previous={data.previous.uniqueVisitors} />
            <KpiCard label="Conversion rate" value={formatPercent(data.current.conversionRate)}
                     current={data.current.conversionRate} previous={data.previous.conversionRate} />
          </AnalyticsCards>
          <AnalyticsCards>
            <KpiCard label="Order value (gross)" value={formatMoney(data.current.money.grossOrderValue)} hint="Non-cancelled orders placed" />
            <KpiCard label="Refunded" value={formatMoney(data.current.money.refunded)} hint={`Net revenue ${formatMoney(data.current.money.netRevenue)}`} />
            <KpiCard label="Registered customers" value={formatNumber(data.totalCustomers)} hint={`${formatNumber(data.current.newCustomers)} new in period`} />
            <KpiCard label="Active now" value={formatNumber(data.activeVisitors)} hint={`Visitors (5 min) · ${formatNumber(data.activeSessions)} signed-in sessions`} />
          </AnalyticsCards>

          <div className="grid gap-6 xl:grid-cols-2">
            <TrendChart title="Paid revenue" description="By payment date (UTC)" money
                        data={data.trend.map((d) => ({ date: d.date, value: Number(d.revenue) }))} valueLabel="Revenue" />
            <TrendChart title="Paid orders" description="By payment date (UTC)"
                        data={data.trend.map((d) => ({ date: d.date, value: d.orders }))} valueLabel="Orders" />
            <FunnelChart funnel={data.funnel} />
            <BarList title="Visitor devices" description="Sessions started in period" items={data.devices} />
          </div>

          <div className="grid gap-6 xl:grid-cols-2">
            <Card>
              <CardHeader><CardTitle>Best sellers</CardTitle></CardHeader>
              <CardContent>
                {data.bestSellers.length === 0 ? <p className="text-sm text-muted-foreground">No paid orders in this period.</p> : (
                  <ol className="space-y-2 text-sm">
                    {data.bestSellers.map((b) => (
                      <li key={b.productId} className="flex justify-between gap-3">
                        <Link href={`/admin/products/${b.productId}`} className="truncate hover:underline">{b.name}</Link>
                        <span className="shrink-0 text-muted-foreground">{b.unitsSold} sold · {formatMoney(b.revenue)}</span>
                      </li>
                    ))}
                  </ol>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle className="flex items-center gap-2"><AlertTriangle className="size-4" aria-hidden /> Low stock</CardTitle></CardHeader>
              <CardContent>
                {data.lowStock.length === 0 ? <p className="text-sm text-muted-foreground">All products are well stocked.</p> : (
                  <ul className="space-y-2 text-sm">
                    {data.lowStock.map((l) => (
                      <li key={l.variantId} className="flex justify-between gap-3">
                        <Link href={`/admin/products/${l.productId}`} className="truncate hover:underline">{l.productName}{l.variant ? ` (${l.variant})` : ""}</Link>
                        <span className="shrink-0">{l.stockQuantity === 0 ? "Out of stock" : `${l.stockQuantity} left`}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </div>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">Recent orders</h2>
            <DataTable rows={data.recentOrders} rowKey={(o) => o.id} emptyTitle="No orders yet" columns={[
              { key: "n", header: "Order", cell: (o) => <Link href={`/admin/orders/${o.id}`} className="font-medium hover:underline">{o.orderNumber}</Link> },
              { key: "c", header: "Customer", cell: (o) => o.customerEmail, hideOnMobile: true },
              { key: "d", header: "Date", cell: (o) => formatDate(o.placedAt), hideOnMobile: true },
              { key: "s", header: "Status", cell: (o) => <StatusBadge status={o.status} /> },
              { key: "p", header: "Payment", cell: (o) => <StatusBadge status={o.paymentStatus} />, hideOnMobile: true },
              { key: "t", header: "Total", className: "text-right", cell: (o) => formatMoney(o.grandTotal, o.currency) },
            ]} />
          </section>
        </div>
      )}
    </div>
  );
}
