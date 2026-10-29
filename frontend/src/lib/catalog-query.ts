/** Translates page search params into a whitelisted backend query string. */
export function catalogQuery(sp: Record<string, string | string[] | undefined>, extra: Record<string, string> = {}): string {
  const allowed = ["q", "categoryId", "minPrice", "maxPrice", "sort", "page"];
  const params = new URLSearchParams();
  for (const key of allowed) {
    const v = sp[key];
    const value = Array.isArray(v) ? v[0] : v;
    if (value) {
      params.set(key, value.slice(0, 100));
    }
  }
  for (const [k, v] of Object.entries(extra)) {
    params.set(k, v);
  }
  params.set("size", "12");
  return params.toString();
}

/** Builds a link to another page of the same listing, keeping the current filters. */
export function pageHref(path: string, sp: Record<string, string | string[] | undefined>, page: number): string {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(sp)) {
    const value = Array.isArray(v) ? v[0] : v;
    if (value && k !== "page") {
      params.set(k, value);
    }
  }
  if (page > 0) {
    params.set("page", String(page));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}
