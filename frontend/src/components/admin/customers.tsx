"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { StatusBadge } from "@/components/account/order-views";
import { DeviceIcon } from "@/components/account/account-settings";
import { ConfirmationDialog } from "@/components/common/confirmation-dialog";
import { DataTable } from "@/components/common/data-table";
import { Checkbox, NativeSelect } from "@/components/common/form";
import { ErrorState, LoadingState } from "@/components/common/states";
import { useAuth } from "@/components/providers/auth-provider";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useApi } from "@/hooks/use-api";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import type { CustomerDetail, Role } from "@/lib/api/types";
import { formatDate, formatDateTime, humanize } from "@/lib/format";

import { PageHeader } from "./admin-shell";

export function CustomerList() {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [role, setRole] = useState<Role | "">("");
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi(() => adminApi.users({ q: search || undefined, role: role || undefined, page }), [search, role, page]);

  return (
    <div className="space-y-6">
      <PageHeader title="Customers & users" />
      <div className="flex flex-wrap gap-2">
        <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); setPage(0); setSearch(q.trim()); }}>
          <Input placeholder="Name or email" aria-label="Search users" value={q} onChange={(e) => setQ(e.target.value)} className="w-64" />
          <Button type="submit" variant="outline">Search</Button>
        </form>
        <NativeSelect aria-label="Role" className="w-40" value={role} onChange={(e) => { setPage(0); setRole(e.target.value as Role | ""); }}>
          <option value="">All roles</option>
          <option value="ROLE_CUSTOMER">Customers</option>
          <option value="ROLE_STAFF">Staff</option>
          <option value="ROLE_ADMIN">Admins</option>
        </NativeSelect>
      </div>
      <DataTable rows={data?.content} loading={loading} error={error} onRetry={reload} rowKey={(u) => u.id}
        onRowClick={(u) => router.push(`/admin/customers/${u.id}`)} emptyTitle="No users found"
        page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements} onPageChange={setPage}
        columns={[
          { key: "n", header: "Name", cell: (u) => <div><p className="font-medium">{u.firstName} {u.lastName}</p><p className="text-xs text-muted-foreground">{u.email}</p></div> },
          { key: "r", header: "Roles", hideOnMobile: true, cell: (u) => u.roles.map((r) => r.replace("ROLE_", "").toLowerCase()).join(", ") },
          { key: "s", header: "Status", cell: (u) => !u.enabled ? <Badge variant="destructive">Deactivated</Badge> : u.locked ? <Badge variant="secondary">Locked</Badge> : <Badge variant="outline">Active</Badge> },
          { key: "l", header: "Last login", hideOnMobile: true, cell: (u) => formatDateTime(u.lastLoginAt) },
          { key: "c", header: "Joined", hideOnMobile: true, cell: (u) => formatDate(u.createdAt) },
        ]} />
    </div>
  );
}

export function CustomerDetailView({ userId }: { userId: number }) {
  const { can, user: me } = useAuth();
  const { data: user, error, reload, setData } = useApi(() => adminApi.user(userId), [userId]);
  const sessions = useApi(() => (can("SECURITY_LOG_READ") ? adminApi.userSessions(userId) : Promise.resolve([])), [userId]);
  const activity = useApi(() => adminApi.userActivity(userId), [userId]);
  const [dialog, setDialog] = useState<"toggle" | "roles" | null>(null);
  const [roles, setRoles] = useState<Role[]>([]);

  if (error) {
    return <ErrorState message={error} onRetry={reload} />;
  }
  if (!user) {
    return <LoadingState />;
  }
  const run = async (fn: () => Promise<CustomerDetail>, msg: string) => {
    try {
      setData(await fn());
      toast.success(msg);
      void sessions.reload();
    } catch (e) {
      toast.error(errorMessage(e));
      throw e;
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader title={`${user.firstName} ${user.lastName}`} description={user.email} actions={
        <>
          <Link href={`/admin/login-history?userId=${user.id}`} className={buttonVariants({ variant: "outline", size: "sm" })}>Login history</Link>
          {user.locked && can("CUSTOMER_WRITE") && <Button size="sm" variant="outline" onClick={() => run(() => adminApi.unlockUser(user.id), "Account unlocked")}>Unlock</Button>}
          {can("ROLE_ASSIGN") && <Button size="sm" variant="outline" onClick={() => { setRoles(user.roles); setDialog("roles"); }}>Roles</Button>}
          {can("CUSTOMER_WRITE") && user.id !== undefined && (
            <Button size="sm" variant={user.enabled ? "destructive" : "default"} onClick={() => setDialog("toggle")}>
              {user.enabled ? "Deactivate" : "Activate"}
            </Button>
          )}
        </>
      } />
      <div className="grid gap-4 md:grid-cols-4">
        <Info label="Status" value={!user.enabled ? "Deactivated" : user.locked ? `Locked until ${formatDateTime(user.lockedUntil)}` : "Active"} />
        <Info label="Email" value={user.emailVerified ? "Verified" : "Not verified"} />
        <Info label="Orders" value={String(user.orderCount)} />
        <Info label="Active sessions" value={String(user.activeSessions)} />
        <Info label="Roles" value={user.roles.map((r) => r.replace("ROLE_", "").toLowerCase()).join(", ")} />
        <Info label="Phone" value={user.phoneNumber ?? "-"} />
        <Info label="Last login" value={formatDateTime(user.lastLoginAt)} />
        <Info label="Joined" value={formatDate(user.createdAt)} />
      </div>

      {can("SECURITY_LOG_READ") && (
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Active sessions</CardTitle>
            {can("SESSION_REVOKE") && (sessions.data?.length ?? 0) > 0 && (
              <Button size="sm" variant="outline" onClick={async () => {
                await adminApi.revokeAllUserSessions(user.id);
                toast.success("All sessions revoked");
                void sessions.reload();
              }}>Revoke all</Button>
            )}
          </CardHeader>
          <CardContent>
            {!sessions.data ? <LoadingState /> : sessions.data.length === 0 ? <p className="text-sm text-muted-foreground">No active sessions.</p> : (
              <ul className="divide-y text-sm">
                {sessions.data.map((s) => (
                  <li key={s.id} className="flex items-center gap-3 py-2">
                    <DeviceIcon device={s.deviceCategory} />
                    <span className="flex-1">{s.browser} on {s.operatingSystem} · {s.ipAddress} · last active {formatDateTime(s.lastSeenAt)}</span>
                    {can("SESSION_REVOKE") && <Button size="xs" variant="outline" onClick={async () => {
                      await adminApi.revokeSession(s.id);
                      void sessions.reload();
                    }}>Revoke</Button>}
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      )}

      <section className="space-y-2">
        <h2 className="font-semibold">Activity</h2>
        <DataTable rows={activity.data?.content} loading={activity.loading} error={activity.error} rowKey={(a) => a.id}
          emptyTitle="No recorded activity" columns={[
            { key: "t", header: "When", cell: (a) => formatDateTime(a.occurredAt) },
            { key: "a", header: "Action", cell: (a) => humanize(a.action) },
            { key: "e", header: "Entity", hideOnMobile: true, cell: (a) => [a.entityType, a.entityId].filter(Boolean).join(" #") },
            { key: "o", header: "Outcome", cell: (a) => <StatusBadge status={a.outcome} /> },
          ]} />
      </section>

      <ConfirmationDialog open={dialog === "toggle"} onOpenChange={(o) => !o && setDialog(null)} destructive={user.enabled}
        title={user.enabled ? "Deactivate this account?" : "Activate this account?"}
        description={user.enabled ? "The user is signed out everywhere immediately and cannot sign in." : "The user will be able to sign in again."}
        confirmLabel={user.enabled ? "Deactivate" : "Activate"}
        onConfirm={() => run(() => adminApi.setUserEnabled(user.id, !user.enabled), user.enabled ? "Account deactivated" : "Account activated")} />
      <ConfirmationDialog open={dialog === "roles"} onOpenChange={(o) => !o && setDialog(null)} title="Assign roles"
        description="Changing roles signs the user out so new permissions apply on next sign-in." confirmLabel="Save roles"
        onConfirm={() => run(() => adminApi.setUserRoles(user.id, roles), "Roles updated")}>
        <div className="space-y-2">
          {(["ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN"] as Role[]).map((r) => (
            <Checkbox key={r} label={humanize(r.replace("ROLE_", ""))} checked={roles.includes(r)}
                      disabled={r === "ROLE_ADMIN" && me?.email === user.email}
                      onChange={(e) => setRoles((cur) => (e.target.checked ? [...cur, r] : cur.filter((x) => x !== r)))} />
          ))}
        </div>
      </ConfirmationDialog>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm font-medium">{value}</p>
    </div>
  );
}
