"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";

import { DataTable } from "@/components/common/data-table";
import { Checkbox, Field, NativeSelect } from "@/components/common/form";
import { ErrorState, LoadingState } from "@/components/common/states";
import { useAuth } from "@/components/providers/auth-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { useApi } from "@/hooks/use-api";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import type { Category, Coupon } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";
import { categorySchema, couponSchema, type CategoryValues, type CouponValues } from "@/lib/validation/schemas";

import { PageHeader } from "./admin-shell";

// ---------------------------------------------------------------- categories

export function CategoryManager() {
  const { can } = useAuth();
  const { data, error, loading, reload } = useApi(() => adminApi.categories(), []);
  const [editing, setEditing] = useState<Category | "new" | null>(null);

  return (
    <div className="space-y-6">
      <PageHeader title="Categories" actions={can("CATALOG_WRITE") && <Button onClick={() => setEditing("new")}><Plus /> New category</Button>} />
      <DataTable rows={data} loading={loading} error={error} onRetry={reload} rowKey={(c) => c.id} emptyTitle="No categories yet" columns={[
        { key: "n", header: "Name", cell: (c) => <span className="font-medium">{c.name}</span> },
        { key: "s", header: "Slug", cell: (c) => c.slug, hideOnMobile: true },
        { key: "p", header: "Parent", cell: (c) => data?.find((x) => x.id === c.parentId)?.name ?? "-", hideOnMobile: true },
        { key: "o", header: "Order", cell: (c) => c.sortOrder, hideOnMobile: true },
        { key: "a", header: "Status", cell: (c) => <Badge variant={c.active ? "default" : "secondary"}>{c.active ? "Active" : "Hidden"}</Badge> },
        { key: "x", header: "", className: "text-right", cell: (c) => can("CATALOG_WRITE") && (
          <div className="flex justify-end gap-1">
            <Button size="icon-sm" variant="ghost" aria-label={`Edit ${c.name}`} onClick={() => setEditing(c)}><Pencil /></Button>
            <Button size="icon-sm" variant="ghost" aria-label={`Delete ${c.name}`} onClick={async () => {
              try {
                await adminApi.deleteCategory(c.id);
                toast.success("Category deleted");
                void reload();
              } catch (e) {
                toast.error(errorMessage(e));
              }
            }}><Trash2 /></Button>
          </div>) },
      ]} />
      {editing && data && (
        <CategoryDialog category={editing === "new" ? null : editing} categories={data}
                        onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void reload(); }} />
      )}
    </div>
  );
}

function CategoryDialog({ category, categories, onClose, onSaved }: {
  category: Category | null; categories: Category[]; onClose: () => void; onSaved: () => void;
}) {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<CategoryValues>({
    resolver: zodResolver(categorySchema),
    defaultValues: {
      name: category?.name ?? "", description: category?.description ?? "", parentId: category?.parentId ? String(category.parentId) : "",
      sortOrder: category?.sortOrder ?? 0, active: category?.active ?? true,
    },
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>{category ? "Edit category" : "New category"}</DialogTitle></DialogHeader>
        <form noValidate className="space-y-4" onSubmit={handleSubmit(async (v) => {
          const body = { name: v.name, description: v.description || null, imageUrl: null, parentId: v.parentId ? Number(v.parentId) : null, sortOrder: v.sortOrder, active: v.active };
          try {
            if (category) {
              await adminApi.updateCategory(category.id, body);
            } else {
              await adminApi.createCategory(body);
            }
            toast.success("Category saved");
            onSaved();
          } catch (e) {
            toast.error(errorMessage(e));
          }
        })}>
          <Field id="c-name" label="Name" error={errors.name?.message}><Input id="c-name" {...register("name")} /></Field>
          <Field id="c-desc" label="Description" error={errors.description?.message}><Input id="c-desc" {...register("description")} /></Field>
          <Field id="c-parent" label="Parent category">
            <NativeSelect id="c-parent" {...register("parentId")}>
              <option value="">None (top level)</option>
              {categories.filter((c) => c.id !== category?.id).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </NativeSelect>
          </Field>
          <Field id="c-order" label="Sort order" error={errors.sortOrder?.message}><Input id="c-order" type="number" min={0} {...register("sortOrder")} /></Field>
          <Checkbox label="Visible in the store" {...register("active")} />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={isSubmitting}>Save</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------- settings: coupons & shipping

export function SettingsView() {
  const settings = useApi(() => adminApi.settings(), []);
  const coupons = useApi(() => adminApi.coupons(), []);
  const [couponDialog, setCouponDialog] = useState(false);

  if (settings.error) {
    return <ErrorState message={settings.error} onRetry={settings.reload} />;
  }
  if (!settings.data) {
    return <LoadingState />;
  }
  const s = settings.data;
  return (
    <div className="space-y-6">
      <PageHeader title="Settings" />
      <Card>
        <CardHeader><CardTitle>Store configuration</CardTitle></CardHeader>
        <CardContent className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <p><span className="text-muted-foreground">Payment provider:</span> {s.paymentProvider}</p>
          <p><span className="text-muted-foreground">Currency:</span> {s.currency}</p>
          <p><span className="text-muted-foreground">Tax rate:</span> {(Number(s.taxRate) * 100).toFixed(2)}%</p>
          <p><span className="text-muted-foreground">Verified email for checkout:</span> {s.requireVerifiedEmailForCheckout ? "Required" : "Not required"}</p>
          <p className="text-xs text-muted-foreground sm:col-span-4">These values come from server environment variables and are changed at deployment.</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>Shipping methods</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {s.shippingMethods.map((m) => <ShippingMethodRow key={m.id} method={m} onSaved={settings.reload} />)}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Coupons</CardTitle>
          <Button size="sm" onClick={() => setCouponDialog(true)}><Plus /> New coupon</Button>
        </CardHeader>
        <CardContent>
          <DataTable rows={coupons.data} loading={coupons.loading} error={coupons.error} rowKey={(c) => c.id} emptyTitle="No coupons" columns={[
            { key: "c", header: "Code", cell: (c) => <span className="font-mono">{c.code}</span> },
            { key: "d", header: "Discount", cell: (c) => describeCoupon(c) },
            { key: "u", header: "Used", cell: (c) => `${c.timesUsed}${c.usageLimit ? ` / ${c.usageLimit}` : ""}` },
            { key: "e", header: "Ends", hideOnMobile: true, cell: (c) => (c.endsAt ? formatDate(c.endsAt) : "-") },
            { key: "a", header: "Status", cell: (c) => <Badge variant={c.active ? "default" : "secondary"}>{c.active ? "Active" : "Inactive"}</Badge> },
            { key: "x", header: "", className: "text-right", cell: (c) => c.active && (
              <Button size="xs" variant="outline" onClick={async () => {
                await adminApi.deactivateCoupon(c.id);
                void coupons.reload();
              }}>Deactivate</Button>) },
          ]} />
        </CardContent>
      </Card>
      {couponDialog && <CouponDialog onClose={() => setCouponDialog(false)} onSaved={() => { setCouponDialog(false); void coupons.reload(); }} />}
    </div>
  );
}

function describeCoupon(c: Coupon): string {
  const base = c.discountType === "PERCENTAGE" ? `${Number(c.discountValue)}% off`
    : c.discountType === "FIXED_AMOUNT" ? `${formatMoney(c.discountValue)} off` : "Free shipping";
  return c.minOrderAmount ? `${base} over ${formatMoney(c.minOrderAmount)}` : base;
}

function ShippingMethodRow({ method, onSaved }: { method: { id: number; code: string; name: string; description: string | null; fee: string; freeShippingThreshold: string | null; estimatedDaysMin: number | null; estimatedDaysMax: number | null; active: boolean }; onSaved: () => void }) {
  const [fee, setFee] = useState(Number(method.fee).toFixed(2));
  const [threshold, setThreshold] = useState(method.freeShippingThreshold ? Number(method.freeShippingThreshold).toFixed(2) : "");
  const [active, setActive] = useState(method.active);
  return (
    <form className="flex flex-wrap items-end gap-3 rounded-lg border p-3" onSubmit={async (e) => {
      e.preventDefault();
      try {
        await adminApi.updateShippingMethod(method.id, {
          name: method.name, description: method.description, fee, freeShippingThreshold: threshold || null,
          estimatedDaysMin: method.estimatedDaysMin, estimatedDaysMax: method.estimatedDaysMax, active,
        });
        toast.success(`${method.name} saved`);
        onSaved();
      } catch (err) {
        toast.error(errorMessage(err));
      }
    }}>
      <div className="min-w-40 flex-1"><p className="font-medium">{method.name}</p><p className="text-xs text-muted-foreground">{method.code}</p></div>
      <Field id={`fee-${method.id}`} label="Fee"><Input id={`fee-${method.id}`} className="w-24" inputMode="decimal" value={fee} onChange={(e) => setFee(e.target.value)} /></Field>
      <Field id={`thr-${method.id}`} label="Free over"><Input id={`thr-${method.id}`} className="w-24" inputMode="decimal" value={threshold} onChange={(e) => setThreshold(e.target.value)} /></Field>
      <Checkbox label="Active" checked={active} onChange={(e) => setActive(e.target.checked)} className="pb-2" />
      <Button type="submit" size="sm" variant="outline">Save</Button>
    </form>
  );
}

function CouponDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { register, handleSubmit, control, formState: { errors, isSubmitting } } = useForm<CouponValues>({
    resolver: zodResolver(couponSchema),
    defaultValues: { code: "", description: "", discountType: "PERCENTAGE", discountValue: "10", minOrderAmount: "", usageLimit: "", perUserLimit: "1", endsAt: "", active: true },
  });
  const type = useWatch({ control, name: "discountType" });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>New coupon</DialogTitle></DialogHeader>
        <form noValidate className="grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit(async (v) => {
          try {
            await adminApi.createCoupon({
              code: v.code.toUpperCase(), description: v.description || null, discountType: v.discountType,
              discountValue: v.discountType === "FREE_SHIPPING" ? "0" : v.discountValue, minOrderAmount: v.minOrderAmount || null,
              usageLimit: v.usageLimit ? Number(v.usageLimit) : null, perUserLimit: v.perUserLimit ? Number(v.perUserLimit) : null,
              endsAt: v.endsAt ? new Date(`${v.endsAt}T23:59:59Z`).toISOString() : null, active: v.active,
            });
            toast.success("Coupon created");
            onSaved();
          } catch (e) {
            toast.error(errorMessage(e));
          }
        })}>
          <Field id="cp-code" label="Code" error={errors.code?.message}><Input id="cp-code" className="uppercase" {...register("code")} /></Field>
          <Field id="cp-type" label="Type">
            <NativeSelect id="cp-type" {...register("discountType")}>
              <option value="PERCENTAGE">Percentage</option>
              <option value="FIXED_AMOUNT">Fixed amount</option>
              <option value="FREE_SHIPPING">Free shipping</option>
            </NativeSelect>
          </Field>
          {type !== "FREE_SHIPPING" && (
            <Field id="cp-value" label={type === "PERCENTAGE" ? "Percent off" : "Amount off"} error={errors.discountValue?.message}>
              <Input id="cp-value" inputMode="decimal" {...register("discountValue")} />
            </Field>
          )}
          <Field id="cp-min" label="Minimum order (optional)" error={errors.minOrderAmount?.message}><Input id="cp-min" inputMode="decimal" {...register("minOrderAmount")} /></Field>
          <Field id="cp-limit" label="Total uses (optional)" error={errors.usageLimit?.message}><Input id="cp-limit" inputMode="numeric" {...register("usageLimit")} /></Field>
          <Field id="cp-user" label="Uses per customer" error={errors.perUserLimit?.message}><Input id="cp-user" inputMode="numeric" {...register("perUserLimit")} /></Field>
          <Field id="cp-end" label="Ends on (optional)"><Input id="cp-end" type="date" {...register("endsAt")} /></Field>
          <Field id="cp-desc" label="Description" className="sm:col-span-2"><Input id="cp-desc" {...register("description")} /></Field>
          <div className="flex justify-end gap-2 sm:col-span-2">
            <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={isSubmitting}>Create coupon</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

