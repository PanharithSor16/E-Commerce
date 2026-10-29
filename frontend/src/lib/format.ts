/** Formatting helpers. Money arrives as decimal strings and is only converted for display. */

export function formatMoney(value: string | number | null | undefined, currency = "USD"): string {
  if (value === null || value === undefined || value === "") {
    return "";
  }
  const n = typeof value === "number" ? value : Number(value);
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(n);
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return "-";
  }
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(iso));
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return "-";
  }
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

export function formatPercent(rate: number, digits = 1): string {
  return `${(rate * 100).toFixed(digits)}%`;
}

export function formatNumber(n: number): string {
  return new Intl.NumberFormat("en-US").format(n);
}

/** Percentage change between two periods, or null when there is no baseline. */
export function change(current: number, previous: number): number | null {
  if (!previous) {
    return null;
  }
  return (current - previous) / previous;
}

export function humanize(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  return value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());
}
