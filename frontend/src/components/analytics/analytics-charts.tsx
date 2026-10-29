"use client";

import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import { useState } from "react";
import type { ReactNode } from "react";
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Funnel, NamedCount } from "@/lib/api/types";
import { change, formatMoney, formatNumber, formatPercent, humanize } from "@/lib/format";

const AXIS = { stroke: "var(--chart-axis)", fontSize: 12, tickLine: false, axisLine: false } as const;
const GRID = <CartesianGrid stroke="var(--chart-grid)" vertical={false} />;
const shortDate = (d: string) => new Date(`${d}T00:00:00Z`).toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone: "UTC" });

// ---------------------------------------------------------------- KPI tiles

export function KpiCard({ label, value, current, previous, hint }: {
  label: string; value: string; current?: number; previous?: number; hint?: string;
}) {
  const delta = current !== undefined && previous !== undefined ? change(current, previous) : null;
  return (
    <Card size="sm">
      <CardHeader><CardTitle className="text-sm font-normal text-muted-foreground">{label}</CardTitle></CardHeader>
      <CardContent className="space-y-1">
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        {delta !== null ? (
          <p className="flex items-center gap-1 text-xs text-muted-foreground">
            {delta >= 0 ? <ArrowUpRight className="size-3.5" aria-hidden /> : <ArrowDownRight className="size-3.5" aria-hidden />}
            <span>{delta >= 0 ? "+" : ""}{formatPercent(delta)} vs previous period</span>
          </p>
        ) : hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
      </CardContent>
    </Card>
  );
}

export function AnalyticsCards({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">{children}</div>;
}

// ---------------------------------------------------------------- chart frame with table view

/** Every chart can be switched to a table (accessibility and exact values). */
export function ChartCard({ title, description, table, children }: {
  title: string; description?: string; table: { headers: string[]; rows: (string | number)[][] }; children: ReactNode;
}) {
  const [showTable, setShowTable] = useState(false);
  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-2">
        <div>
          <CardTitle>{title}</CardTitle>
          {description && <p className="mt-1 text-xs text-muted-foreground">{description}</p>}
        </div>
        <Button variant="ghost" size="xs" onClick={() => setShowTable((v) => !v)} aria-pressed={showTable}>
          {showTable ? "Chart" : "Table"}
        </Button>
      </CardHeader>
      <CardContent>
        {showTable ? (
          <div className="max-h-72 overflow-auto">
            <Table>
              <TableHeader><TableRow>{table.headers.map((h) => <TableHead key={h}>{h}</TableHead>)}</TableRow></TableHeader>
              <TableBody>{table.rows.map((r, i) => <TableRow key={i}>{r.map((c, j) => <TableCell key={j}>{c}</TableCell>)}</TableRow>)}</TableBody>
            </Table>
          </div>
        ) : <div className="h-64 w-full">{children}</div>}
      </CardContent>
    </Card>
  );
}

function TooltipBox({ active, payload, label, format }: {
  active?: boolean; payload?: { name?: string; value?: number | string; color?: string }[]; label?: string; format: (v: number) => string;
}) {
  if (!active || !payload?.length) {
    return null;
  }
  return (
    <div className="rounded-md border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      {label && <p className="mb-1 font-medium">{/^\d{4}-\d{2}-\d{2}$/.test(label) ? shortDate(label) : label}</p>}
      {payload.map((p) => (
        <p key={p.name} className="flex items-center gap-2">
          <span className="inline-block size-2 rounded-full" style={{ background: p.color }} aria-hidden />
          <span className="text-muted-foreground">{p.name}</span>
          <span className="ml-auto font-medium tabular-nums">{format(Number(p.value))}</span>
        </p>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- time series

/** Single-measure trend (area). One series: the card title names it, no legend. */
export function TrendChart({ title, description, data, valueLabel, money = false, currency = "USD" }: {
  title: string; description?: string; data: { date: string; value: number }[]; valueLabel: string; money?: boolean; currency?: string;
}) {
  const fmt = (v: number) => (money ? formatMoney(v, currency) : formatNumber(v));
  return (
    <ChartCard title={title} description={description}
               table={{ headers: ["Date", valueLabel], rows: data.map((d) => [d.date, fmt(d.value)]) }}>
      <ResponsiveContainer>
        <AreaChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          {GRID}
          <XAxis dataKey="date" tickFormatter={shortDate} minTickGap={24} {...AXIS} />
          <YAxis width={56} tickFormatter={(v) => (money ? `$${formatNumber(v)}` : formatNumber(v))} allowDecimals={!money ? false : true} {...AXIS} />
          <Tooltip content={<TooltipBox format={fmt} />} cursor={{ stroke: "var(--chart-axis)", strokeDasharray: "3 3" }} />
          <Area type="monotone" dataKey="value" name={valueLabel} stroke="var(--series-1)" strokeWidth={2}
                fill="var(--series-1)" fillOpacity={0.12} activeDot={{ r: 4 }} />
        </AreaChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

/** Up to three series sharing one axis and one unit (e.g. paid vs refunded revenue). */
export function MultiLineChart({ title, description, data, series, money = false, currency = "USD" }: {
  title: string; description?: string; data: Record<string, number | string>[];
  series: { key: string; label: string }[]; money?: boolean; currency?: string;
}) {
  const fmt = (v: number) => (money ? formatMoney(v, currency) : formatNumber(v));
  const colors = ["var(--series-1)", "var(--series-2)", "var(--series-3)"];
  return (
    <ChartCard title={title} description={description} table={{
      headers: ["Date", ...series.map((s) => s.label)],
      rows: data.map((d) => [String(d.date), ...series.map((s) => fmt(Number(d[s.key])))]),
    }}>
      <ResponsiveContainer>
        <LineChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          {GRID}
          <XAxis dataKey="date" tickFormatter={shortDate} minTickGap={24} {...AXIS} />
          <YAxis width={56} tickFormatter={(v) => (money ? `$${formatNumber(v)}` : formatNumber(v))} {...AXIS} />
          <Tooltip content={<TooltipBox format={fmt} />} cursor={{ stroke: "var(--chart-axis)", strokeDasharray: "3 3" }} />
          <Legend iconType="plainline" wrapperStyle={{ fontSize: 12 }} />
          {series.slice(0, 3).map((s, i) => (
            <Line key={s.key} type="monotone" dataKey={s.key} name={s.label} stroke={colors[i]} strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

// ---------------------------------------------------------------- categories & funnel

/** Magnitude by category: horizontal bars in a single hue (identity comes from the label). */
export function BarList({ title, description, items, emptyText = "No data for this period" }: {
  title: string; description?: string; items: NamedCount[]; emptyText?: string;
}) {
  const data = items.map((i) => ({ name: humanize(i.name) === "-" ? "(none)" : i.name.includes("/") ? i.name : humanize(i.name), count: i.count }));
  return (
    <ChartCard title={title} description={description}
               table={{ headers: ["Name", "Count"], rows: data.map((d) => [d.name, formatNumber(d.count)]) }}>
      {data.length === 0 ? <p className="py-20 text-center text-sm text-muted-foreground">{emptyText}</p> : (
        <ResponsiveContainer>
          <BarChart data={data} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }} barCategoryGap={6}>
            <CartesianGrid stroke="var(--chart-grid)" horizontal={false} />
            <XAxis type="number" allowDecimals={false} {...AXIS} />
            <YAxis type="category" dataKey="name" width={120} {...AXIS} />
            <Tooltip content={<TooltipBox format={formatNumber} />} cursor={{ fill: "var(--chart-grid)", opacity: 0.5 }} />
            <Bar dataKey="count" name="Count" fill="var(--series-1)" radius={[0, 4, 4, 0]} maxBarSize={22} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

export function FunnelChart({ funnel }: { funnel: Funnel }) {
  const data = funnel.steps.map((s) => ({ step: s.step, sessions: s.sessions, rate: s.rateFromStart }));
  return (
    <ChartCard title="Conversion funnel"
               description={`Sessions reaching each step · conversion ${formatPercent(funnel.conversionRate)} · cart abandonment (estimate) ${formatPercent(funnel.cartAbandonmentEstimate)}`}
               table={{
                 headers: ["Step", "Sessions", "From start", "From previous"],
                 rows: funnel.steps.map((s) => [s.step, formatNumber(s.sessions), formatPercent(s.rateFromStart), formatPercent(s.rateFromPrevious)]),
               }}>
      <ResponsiveContainer>
        <BarChart data={data} margin={{ top: 20, right: 8, left: 0, bottom: 0 }} barCategoryGap="20%">
          {GRID}
          <XAxis dataKey="step" interval={0} {...AXIS} />
          <YAxis width={48} allowDecimals={false} {...AXIS} />
          <Tooltip content={<TooltipBox format={formatNumber} />} cursor={{ fill: "var(--chart-grid)", opacity: 0.5 }} />
          <Bar dataKey="sessions" name="Sessions" fill="var(--series-1)" radius={[4, 4, 0, 0]} maxBarSize={56}
               label={{ position: "top", fontSize: 11, fill: "var(--chart-axis)", formatter: (v: unknown) => formatNumber(Number(v)) }} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
