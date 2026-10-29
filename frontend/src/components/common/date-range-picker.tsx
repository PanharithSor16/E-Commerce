"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import type { DateRangeQuery } from "@/lib/api/types";

import { NativeSelect } from "./form";

const PRESETS: { value: NonNullable<DateRangeQuery["range"]> | "custom"; label: string }[] = [
  { value: "today", label: "Today" },
  { value: "7d", label: "Last 7 days" },
  { value: "30d", label: "Last 30 days" },
  { value: "this_month", label: "Current month" },
  { value: "last_month", label: "Previous month" },
  { value: "custom", label: "Custom range" },
];

/** Preset or custom (inclusive, UTC) date range for reports. */
export function DateRangePicker({ value, onChange }: { value: DateRangeQuery; onChange: (v: DateRangeQuery) => void }) {
  const [mode, setMode] = useState<string>(value.from ? "custom" : value.range ?? "30d");
  const [from, setFrom] = useState(value.from ?? "");
  const [to, setTo] = useState(value.to ?? "");

  return (
    <div className="flex flex-wrap items-end gap-2">
      <div>
        <label htmlFor="range-preset" className="mb-1 block text-xs text-muted-foreground">Period</label>
        <NativeSelect id="range-preset" value={mode} onChange={(e) => {
          const m = e.target.value;
          setMode(m);
          if (m !== "custom") {
            onChange({ range: m as DateRangeQuery["range"] });
          }
        }}>
          {PRESETS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
        </NativeSelect>
      </div>
      {mode === "custom" && (
        <>
          <div>
            <label htmlFor="range-from" className="mb-1 block text-xs text-muted-foreground">From</label>
            <input id="range-from" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)}
                   className="h-8 rounded-lg border bg-background px-2 text-sm" />
          </div>
          <div>
            <label htmlFor="range-to" className="mb-1 block text-xs text-muted-foreground">To</label>
            <input id="range-to" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)}
                   className="h-8 rounded-lg border bg-background px-2 text-sm" />
          </div>
          <Button size="sm" disabled={!from || !to} onClick={() => onChange({ from, to })}>Apply</Button>
        </>
      )}
    </div>
  );
}
