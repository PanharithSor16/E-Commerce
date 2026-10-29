"use client";

import { Download } from "lucide-react";
import { useState } from "react";

import { AnalyticsCards, BarList, FunnelChart, KpiCard, MultiLineChart } from "@/components/analytics/analytics-charts";
import { DataTable } from "@/components/common/data-table";
import { DateRangePicker } from "@/components/common/date-range-picker";
import { ErrorState, LoadingState } from "@/components/common/states";
import { useAuth } from "@/components/providers/auth-provider";
import { buttonVariants } from "@/components/ui/button";
import { useApi } from "@/hooks/use-api";
import { reportsApi } from "@/lib/api/endpoints";
import type { DateRangeQuery } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { formatDateTime, formatMoney, formatNumber, formatPercent } from "@/lib/format";

import { PageHeader } from "./admin-shell";

const TABS = ["Visitors", "Products", "Sales", "Conversion", "Logins"] as const;
type Tab = (typeof TABS)[number];

export function AnalyticsReports() {
  const { can } = useAuth();
  const [tab, setTab] = useState<Tab>("Visitors");
  const [range, setRange] = useState<DateRangeQuery>({ range: "30d" });
  const tabs = TABS.filter((t) => t !== "Logins" || can("SECURITY_LOG_READ"));
  const exportable = { Visitors: "visitors", Products: "products", Sales: "sales" } as const;

  return (
    <div className="space-y-6">
      <PageHeader title="Analytics" description="All windows are UTC days. Visitors are counted by distinct consented visitor id, not by events."
        actions={
          <>
            <DateRangePicker value={range} onChange={setRange} />
            {can("ANALYTICS_EXPORT") && tab in exportable && (
              <a href={reportsApi.exportUrl(exportable[tab as keyof typeof exportable], range)}
                 className={cn(buttonVariants({ variant: "outline", size: "sm" }), "self-end")}>
                <Download /> Export CSV
              </a>
            )}
          </>
        } />
      <div role="tablist" aria-label="Reports" className="flex gap-1 overflow-x-auto border-b">
        {tabs.map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
                  className={cn("shrink-0 border-b-2 px-3 py-2 text-sm", tab === t ? "border-primary font-medium" : "border-transparent text-muted-foreground")}>
            {t}
          </button>
        ))}
      </div>
      <div role="tabpanel">
        {tab === "Visitors" && <VisitorsTab range={range} />}
        {tab === "Products" && <ProductsTab range={range} />}
        {tab === "Sales" && <SalesTab range={range} />}
        {tab === "Conversion" && <ConversionTab range={range} />}
        {tab === "Logins" && <LoginsTab range={range} />}
      </div>
    </div>
  );
}

const deps = (r: DateRangeQuery) => [r.range, r.from, r.to];

function VisitorsTab({ range }: { range: DateRangeQuery }) {
  const { data, error, reload } = useApi(() => reportsApi.visitors(range), deps(range));
  if (error) return <ErrorState message={error} onRetry={reload} />;
  if (!data) return <LoadingState />;
  return (
    <div className="space-y-6">
      <AnalyticsCards>
        <KpiCard label="Unique visitors" value={formatNumber(data.uniqueVisitors)} hint="Distinct visitor ids" />
        <KpiCard label="Sessions" value={formatNumber(data.sessions)} hint="30-minute inactivity timeout" />
        <KpiCard label="Page views" value={formatNumber(data.pageViews)} />
        <KpiCard label="New visitors" value={formatNumber(data.newVisitors)} hint="First seen in period" />
      </AnalyticsCards>
      <MultiLineChart title="Daily traffic" data={data.daily.map((d) => ({ date: d.date, pageViews: d.pageViews, visitors: d.uniqueVisitors, sessions: d.sessions }))}
                      series={[{ key: "pageViews", label: "Page views" }, { key: "sessions", label: "Sessions" }, { key: "visitors", label: "Unique visitors" }]} />
      <div className="grid gap-6 xl:grid-cols-2">
        <BarList title="Traffic sources" items={data.trafficSources} />
        <BarList title="Devices" items={data.devices} />
        <BarList title="Browsers" items={data.browsers} />
        <BarList title="Top pages" items={data.topPages} />
        <BarList title="Campaigns" description="utm_campaign" items={data.campaigns} />
        <BarList title="Referrers" items={data.referrers} />
      </div>
    </div>
  );
}

function ProductsTab({ range }: { range: DateRangeQuery }) {
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi(() => reportsApi.products({ ...range, page }), [...deps(range), page]);
  return (
    <DataTable rows={data?.content} loading={loading} error={error} onRetry={reload} rowKey={(r) => r.productId}
      emptyTitle="No product activity in this period" page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements}
      onPageChange={setPage} columns={[
        { key: "n", header: "Product", cell: (r) => <span className="font-medium">{r.name}</span> },
        { key: "v", header: "Views", className: "text-right", cell: (r) => formatNumber(r.views) },
        { key: "u", header: "Unique viewers", className: "text-right", cell: (r) => formatNumber(r.uniqueViewers), hideOnMobile: true },
        { key: "a", header: "Add to cart", className: "text-right", cell: (r) => formatNumber(r.addToCarts), hideOnMobile: true },
        { key: "s", header: "Units sold", className: "text-right", cell: (r) => formatNumber(r.unitsSold) },
        { key: "r", header: "Revenue", className: "text-right", cell: (r) => formatMoney(r.revenue) },
        { key: "c", header: "View → purchase", className: "text-right", cell: (r) => formatPercent(r.viewToPurchaseRate), hideOnMobile: true },
      ]} />
  );
}

function SalesTab({ range }: { range: DateRangeQuery }) {
  const { data, error, reload } = useApi(() => reportsApi.sales(range), deps(range));
  if (error) return <ErrorState message={error} onRetry={reload} />;
  if (!data) return <LoadingState />;
  return (
    <div className="space-y-6">
      <AnalyticsCards>
        <KpiCard label="Paid revenue" value={formatMoney(data.money.paidRevenue)} hint={`${formatNumber(data.paidOrders)} paid orders`} />
        <KpiCard label="Order value (gross)" value={formatMoney(data.money.grossOrderValue)} hint={`${formatNumber(data.ordersPlaced)} orders placed`} />
        <KpiCard label="Refunded" value={formatMoney(data.money.refunded)} hint={`Net ${formatMoney(data.money.netRevenue)}`} />
        <KpiCard label="Average order value" value={formatMoney(data.averageOrderValue)} hint="Paid orders" />
      </AnalyticsCards>
      <MultiLineChart title="Daily sales" money description="Gross by order date; paid by payment date; refunds by completion date"
        data={data.daily.map((d) => ({ date: d.date, gross: Number(d.grossOrderValue), paid: Number(d.paidRevenue), refunded: Number(d.refunded) }))}
        series={[{ key: "paid", label: "Paid revenue" }, { key: "gross", label: "Order value (gross)" }, { key: "refunded", label: "Refunded" }]} />
      <div className="grid gap-6 xl:grid-cols-2">
        <BarList title="Orders by status" items={data.ordersByStatus} />
        <BarList title="Units sold by category" items={data.topCategories.map((c) => ({ name: c.name, count: c.unitsSold }))} />
      </div>
    </div>
  );
}

function ConversionTab({ range }: { range: DateRangeQuery }) {
  const { data, error, reload } = useApi(() => reportsApi.conversion(range), deps(range));
  if (error) return <ErrorState message={error} onRetry={reload} />;
  if (!data) return <LoadingState />;
  return (
    <div className="space-y-6">
      <FunnelChart funnel={data} />
      <p className="text-sm text-muted-foreground">
        Steps count distinct visitor sessions started in the period. Purchases come from confirmed payments recorded by
        the server, never from browser events. Cart abandonment is an estimate: 1 − purchase sessions ÷ add-to-cart sessions.
      </p>
    </div>
  );
}

function LoginsTab({ range }: { range: DateRangeQuery }) {
  const { data, error, reload } = useApi(() => reportsApi.logins(range), deps(range));
  if (error) return <ErrorState message={error} onRetry={reload} />;
  if (!data) return <LoadingState />;
  return (
    <div className="space-y-6">
      <AnalyticsCards>
        <KpiCard label="Successful logins" value={formatNumber(data.successes)} />
        <KpiCard label="Failed attempts" value={formatNumber(data.failures)} />
        <KpiCard label="Users who signed in" value={formatNumber(data.uniqueUsers)} />
        <KpiCard label="Active sessions now" value={formatNumber(data.activeSessions)} hint="Not the same as login attempts" />
      </AnalyticsCards>
      <MultiLineChart title="Login attempts per day" data={data.daily.map((d) => ({ date: d.date, ok: d.successes, failed: d.failures }))}
                      series={[{ key: "ok", label: "Successful" }, { key: "failed", label: "Failed" }]} />
      <div className="grid gap-6 xl:grid-cols-2">
        <BarList title="Failure reasons" items={data.failureReasons} />
        <BarList title="Sign-in devices" items={data.devices} />
      </div>
      <section className="space-y-2">
        <h2 className="font-semibold">Repeated failures (3+ in period)</h2>
        <DataTable rows={data.repeatedFailures} rowKey={(r) => r.emailHash} emptyTitle="No repeated failures" columns={[
          { key: "e", header: "Email", cell: (r) => r.emailMasked },
          { key: "f", header: "Failures", className: "text-right", cell: (r) => r.failures },
          { key: "i", header: "Distinct IPs", className: "text-right", cell: (r) => r.distinctIps, hideOnMobile: true },
          { key: "l", header: "Last attempt", cell: (r) => formatDateTime(r.lastAttemptAt) },
        ]} />
      </section>
    </div>
  );
}
