"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { StatusBadge } from "@/components/account/order-views";
import { DataTable } from "@/components/common/data-table";
import { NativeSelect } from "@/components/common/form";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useApi } from "@/hooks/use-api";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import { formatDateTime, humanize } from "@/lib/format";

import { PageHeader } from "./admin-shell";

export function LoginHistoryView() {
  const params = useSearchParams();
  const userId = params.get("userId") ? Number(params.get("userId")) : undefined;
  const [form, setForm] = useState({ email: "", ip: "", from: "", to: "" });
  const [filters, setFilters] = useState<{ email?: string; ip?: string; status?: string; from?: string; to?: string }>({});
  const [page, setPage] = useState(0);
  const history = useApi(() => adminApi.loginHistory({ ...filters, userId, page }), [JSON.stringify(filters), userId, page]);
  const repeated = useApi(() => adminApi.repeatedFailures(24, 3), []);

  return (
    <div className="space-y-6">
      <PageHeader title="Login history" description="Every sign-in attempt. Emails are stored masked and searched by keyed hash (exact match)." />
      <Card>
        <CardHeader><CardTitle>Repeated failed attempts (last 24 hours)</CardTitle></CardHeader>
        <CardContent>
          <DataTable rows={repeated.data} loading={repeated.loading} error={repeated.error} rowKey={(r) => r.emailHash}
            emptyTitle="No repeated failures" columns={[
              { key: "e", header: "Email", cell: (r) => r.userId ? <Link className="hover:underline" href={`/admin/customers/${r.userId}`}>{r.emailMasked}</Link> : `${r.emailMasked} (no account)` },
              { key: "f", header: "Failures", className: "text-right", cell: (r) => r.failures },
              { key: "i", header: "Distinct IPs", className: "text-right", cell: (r) => r.distinctIps },
              { key: "l", header: "Last attempt", cell: (r) => formatDateTime(r.lastAttemptAt) },
            ]} />
        </CardContent>
      </Card>
      <form className="flex flex-wrap items-end gap-2" onSubmit={(e) => {
        e.preventDefault();
        setPage(0);
        setFilters((f) => ({ ...f, email: form.email.trim() || undefined, ip: form.ip.trim() || undefined, from: form.from || undefined, to: form.to || undefined }));
      }}>
        <Input placeholder="Exact email" aria-label="Email" type="email" className="w-56" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        <Input placeholder="IP address" aria-label="IP address" className="w-36" value={form.ip} onChange={(e) => setForm({ ...form, ip: e.target.value })} />
        <input type="date" aria-label="From date" className="h-8 rounded-lg border bg-background px-2 text-sm" value={form.from} onChange={(e) => setForm({ ...form, from: e.target.value })} />
        <input type="date" aria-label="To date" className="h-8 rounded-lg border bg-background px-2 text-sm" value={form.to} onChange={(e) => setForm({ ...form, to: e.target.value })} />
        <NativeSelect aria-label="Status" className="w-36" value={filters.status ?? ""} onChange={(e) => { setPage(0); setFilters((f) => ({ ...f, status: e.target.value || undefined })); }}>
          <option value="">All attempts</option>
          <option value="SUCCESS">Successful</option>
          <option value="FAILURE">Failed</option>
        </NativeSelect>
        <Button type="submit" variant="outline">Filter</Button>
        {userId && <Link href="/admin/login-history" className="text-sm underline">Clear user filter</Link>}
      </form>
      <DataTable rows={history.data?.content} loading={history.loading} error={history.error} onRetry={history.reload}
        rowKey={(h) => h.id} emptyTitle="No login attempts match" page={history.data?.page} totalPages={history.data?.totalPages}
        totalElements={history.data?.totalElements} onPageChange={setPage} columns={[
          { key: "t", header: "Time", cell: (h) => formatDateTime(h.occurredAt) },
          { key: "e", header: "Email", cell: (h) => h.userId ? <Link className="hover:underline" href={`/admin/customers/${h.userId}`}>{h.emailMasked}</Link> : h.emailMasked },
          { key: "s", header: "Result", cell: (h) => <StatusBadge status={h.status} /> },
          { key: "r", header: "Reason", cell: (h) => humanize(h.failureReason), hideOnMobile: true },
          { key: "ip", header: "IP", cell: (h) => h.ipAddress ?? "-", hideOnMobile: true },
          { key: "d", header: "Device", hideOnMobile: true, cell: (h) => [h.browser && `${h.browser} ${h.browserVersion ?? ""}`.trim(), h.operatingSystem, humanize(h.deviceCategory)].filter(Boolean).join(" · ") },
        ]} />
    </div>
  );
}

export function SessionsView() {
  const { can } = useAuth();
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi(() => adminApi.sessions(page), [page]);
  return (
    <div className="space-y-6">
      <PageHeader title="Active sessions" description="Signed-in sessions that are not expired or revoked." />
      <DataTable rows={data?.content} loading={loading} error={error} onRetry={reload} rowKey={(s) => s.id}
        emptyTitle="No active sessions" page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements}
        onPageChange={setPage} columns={[
          { key: "u", header: "User", cell: (s) => <Link href={`/admin/customers/${s.userId}`} className="hover:underline">{s.userEmail}</Link> },
          { key: "d", header: "Device", hideOnMobile: true, cell: (s) => `${s.browser ?? "?"} on ${s.operatingSystem ?? "?"}` },
          { key: "ip", header: "IP", hideOnMobile: true, cell: (s) => s.ipAddress ?? "-" },
          { key: "c", header: "Signed in", hideOnMobile: true, cell: (s) => formatDateTime(s.createdAt) },
          { key: "l", header: "Last active", cell: (s) => formatDateTime(s.lastSeenAt) },
          { key: "x", header: "", className: "text-right", cell: (s) => can("SESSION_REVOKE") && (
            <Button size="xs" variant="outline" onClick={async (e) => {
              e.stopPropagation();
              try {
                await adminApi.revokeSession(s.id);
                toast.success("Session revoked");
                void reload();
              } catch (err) {
                toast.error(errorMessage(err));
              }
            }}>Revoke</Button>) },
        ]} />
    </div>
  );
}
