"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Star, Trash2, Upload } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { ConfirmationDialog } from "@/components/common/confirmation-dialog";
import { DataTable } from "@/components/common/data-table";
import { Checkbox, Field, NativeSelect } from "@/components/common/form";
import { ErrorState, LoadingState } from "@/components/common/states";
import { ProductImage } from "@/components/product/product-card";
import { useAuth } from "@/components/providers/auth-provider";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useApi } from "@/hooks/use-api";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import type { AdminProduct, Category, ProductStatus } from "@/lib/api/types";
import { formatMoney, humanize } from "@/lib/format";
import { productSchema, type ProductValues } from "@/lib/validation/schemas";

import { PageHeader } from "./admin-shell";

export function ProductList() {
  const router = useRouter();
  const { can } = useAuth();
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<ProductStatus | "">("");
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useApi(
    () => adminApi.products({ q: search || undefined, status: status || undefined, page }), [search, status, page]);

  return (
    <div className="space-y-6">
      <PageHeader title="Products" actions={can("CATALOG_WRITE") &&
        <Link href="/admin/products/new" className={buttonVariants()}><Plus /> New product</Link>} />
      <div className="flex flex-wrap gap-2">
        <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); setPage(0); setSearch(q.trim()); }}>
          <Input placeholder="Search name or SKU" aria-label="Search products" value={q} onChange={(e) => setQ(e.target.value)} className="w-64" />
          <Button type="submit" variant="outline">Search</Button>
        </form>
        <NativeSelect aria-label="Status" value={status} className="w-40" onChange={(e) => { setPage(0); setStatus(e.target.value as ProductStatus | ""); }}>
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="DRAFT">Draft</option>
          <option value="ARCHIVED">Archived</option>
        </NativeSelect>
      </div>
      <DataTable rows={data?.content} loading={loading} error={error} onRetry={reload} rowKey={(p) => p.id}
        onRowClick={(p) => router.push(`/admin/products/${p.id}`)} emptyTitle="No products found"
        page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements} onPageChange={setPage}
        columns={[
          { key: "img", header: "", className: "w-12", hideOnMobile: true, cell: (p) => (
            <ProductImage src={p.images.find((i) => i.primary)?.url ?? p.images[0]?.url ?? null} alt="" className="size-10 rounded" />) },
          { key: "name", header: "Name", cell: (p) => <div><p className="font-medium">{p.name}</p><p className="text-xs text-muted-foreground">{p.sku}</p></div> },
          { key: "cat", header: "Category", cell: (p) => p.categoryName, hideOnMobile: true },
          { key: "price", header: "Price", cell: (p) => formatMoney(p.discountPrice ?? p.basePrice, p.currency) },
          { key: "stock", header: "Stock", className: "text-right", cell: (p) => (
            <span className={p.totalStock === 0 ? "text-destructive" : undefined}>{p.totalStock}</span>) },
          { key: "status", header: "Status", cell: (p) => <Badge variant={p.status === "ACTIVE" ? "default" : "secondary"}>{humanize(p.status)}</Badge> },
        ]} />
    </div>
  );
}

export function ProductEditor({ productId }: { productId?: number }) {
  const categories = useApi(() => adminApi.categories(), []);
  const product = useApi(() => (productId ? adminApi.product(productId) : Promise.resolve(null)), [productId]);

  if (categories.error || product.error) {
    return <ErrorState message={categories.error ?? product.error ?? ""} />;
  }
  if (!categories.data || product.data === undefined) {
    return <LoadingState />;
  }
  const p = product.data;
  return (
    <div className="space-y-6">
      <PageHeader title={p ? p.name : "New product"} description={p ? `SKU ${p.sku} · ${p.soldCount} sold` : undefined}
                  actions={p && <Link href={`/products/${p.id}`} className={buttonVariants({ variant: "outline" })} target="_blank">View in store</Link>} />
      <ProductForm product={p} categories={categories.data} onSaved={(saved) => product.setData(saved)} />
      {p && (
        <>
          <VariantsCard product={p} onChange={(saved) => product.setData(saved)} />
          <ImagesCard product={p} onChange={(saved) => product.setData(saved)} />
        </>
      )}
    </div>
  );
}

function ProductForm({ product, categories, onSaved }: { product: AdminProduct | null; categories: Category[]; onSaved: (p: AdminProduct) => void }) {
  const router = useRouter();
  const { can } = useAuth();
  const [archiveOpen, setArchiveOpen] = useState(false);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<ProductValues>({
    resolver: zodResolver(productSchema),
    defaultValues: product ? {
      name: product.name, sku: product.sku, categoryId: product.categoryId, brand: product.brand ?? "",
      shortDescription: product.shortDescription ?? "", description: product.description ?? "",
      basePrice: Number(product.basePrice).toFixed(2), discountPrice: product.discountPrice ? Number(product.discountPrice).toFixed(2) : "",
      status: product.status, featured: product.featured,
    } : { name: "", sku: "", categoryId: categories[0]?.id ?? 0, brand: "", shortDescription: "", description: "", basePrice: "", discountPrice: "", status: "DRAFT", featured: false, initialStock: 0 },
  });
  const readOnly = !can("CATALOG_WRITE");

  return (
    <Card>
      <CardHeader><CardTitle>Details</CardTitle></CardHeader>
      <CardContent>
        <form noValidate className="grid gap-4 md:grid-cols-2" onSubmit={handleSubmit(async (v) => {
          const body = {
            ...v, brand: v.brand || undefined, shortDescription: v.shortDescription || undefined, description: v.description || undefined,
            discountPrice: v.discountPrice || null, initialStock: product ? undefined : v.initialStock,
          };
          try {
            const saved = product ? await adminApi.updateProduct(product.id, body) : await adminApi.createProduct(body);
            toast.success("Product saved");
            if (product) {
              onSaved(saved);
            } else {
              router.replace(`/admin/products/${saved.id}`);
            }
          } catch (e) {
            toast.error(errorMessage(e));
          }
        })}>
          <fieldset disabled={readOnly} className="contents">
            <Field id="name" label="Name" error={errors.name?.message} className="md:col-span-2"><Input id="name" {...register("name")} /></Field>
            <Field id="sku" label="SKU" error={errors.sku?.message}><Input id="sku" {...register("sku")} /></Field>
            <Field id="category" label="Category" error={errors.categoryId?.message}>
              <NativeSelect id="category" {...register("categoryId")}>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}{c.active ? "" : " (inactive)"}</option>)}
              </NativeSelect>
            </Field>
            <Field id="brand" label="Brand" error={errors.brand?.message}><Input id="brand" {...register("brand")} /></Field>
            <Field id="status" label="Status" error={errors.status?.message}>
              <NativeSelect id="status" {...register("status")}>
                <option value="DRAFT">Draft (hidden)</option>
                <option value="ACTIVE">Active (visible in store)</option>
                <option value="ARCHIVED">Archived</option>
              </NativeSelect>
            </Field>
            <Field id="basePrice" label="Price" error={errors.basePrice?.message}><Input id="basePrice" inputMode="decimal" {...register("basePrice")} /></Field>
            <Field id="discountPrice" label="Sale price (optional)" error={errors.discountPrice?.message}><Input id="discountPrice" inputMode="decimal" {...register("discountPrice")} /></Field>
            {!product && (
              <Field id="initialStock" label="Initial stock" hint="For the default variant; add size/color variants after saving." error={errors.initialStock?.message}>
                <Input id="initialStock" type="number" min={0} {...register("initialStock")} />
              </Field>
            )}
            <Field id="short" label="Short description" error={errors.shortDescription?.message} className="md:col-span-2"><Input id="short" {...register("shortDescription")} /></Field>
            <Field id="description" label="Description" hint="Plain text." error={errors.description?.message} className="md:col-span-2">
              <Textarea id="description" rows={6} {...register("description")} />
            </Field>
            <Checkbox label="Featured on the homepage" {...register("featured")} />
          </fieldset>
          {!readOnly && (
            <div className="flex gap-2 md:col-span-2">
              <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : "Save product"}</Button>
              {product && product.status !== "ARCHIVED" && (
                <Button type="button" variant="destructive" onClick={() => setArchiveOpen(true)}>Archive</Button>
              )}
            </div>
          )}
        </form>
        {product && (
          <ConfirmationDialog open={archiveOpen} onOpenChange={setArchiveOpen} destructive confirmLabel="Archive product"
            title={`Archive ${product.name}?`} description="It will be hidden from the store. Past orders keep referencing it."
            onConfirm={async () => {
              await adminApi.archiveProduct(product.id);
              onSaved(await adminApi.product(product.id));
              toast.success("Product archived");
            }} />
        )}
      </CardContent>
    </Card>
  );
}

function VariantsCard({ product, onChange }: { product: AdminProduct; onChange: (p: AdminProduct) => void }) {
  const { can } = useAuth();
  const [adding, setAdding] = useState(false);
  const [stockFor, setStockFor] = useState<number | null>(null);
  const [change, setChange] = useState("");
  const [reason, setReason] = useState("RESTOCK");
  const [variant, setVariant] = useState({ sku: "", size: "", color: "", priceOverride: "", initialStock: "0" });

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Variants &amp; stock</CardTitle>
        {can("CATALOG_WRITE") && <Button size="sm" variant="outline" onClick={() => setAdding((v) => !v)}><Plus /> Add variant</Button>}
      </CardHeader>
      <CardContent className="space-y-4">
        {adding && (
          <form className="grid gap-2 rounded-lg border p-3 sm:grid-cols-6" onSubmit={async (e) => {
            e.preventDefault();
            try {
              onChange(await adminApi.addVariant(product.id, {
                sku: variant.sku, size: variant.size || undefined, color: variant.color || undefined,
                priceOverride: variant.priceOverride || null, initialStock: Number(variant.initialStock) || 0,
              }));
              setAdding(false);
              setVariant({ sku: "", size: "", color: "", priceOverride: "", initialStock: "0" });
            } catch (err) {
              toast.error(errorMessage(err));
            }
          }}>
            <Input required placeholder="SKU" aria-label="Variant SKU" value={variant.sku} onChange={(e) => setVariant({ ...variant, sku: e.target.value })} />
            <Input placeholder="Size" aria-label="Size" value={variant.size} onChange={(e) => setVariant({ ...variant, size: e.target.value })} />
            <Input placeholder="Color" aria-label="Color" value={variant.color} onChange={(e) => setVariant({ ...variant, color: e.target.value })} />
            <Input placeholder="Price override" aria-label="Price override" inputMode="decimal" value={variant.priceOverride} onChange={(e) => setVariant({ ...variant, priceOverride: e.target.value })} />
            <Input type="number" min={0} aria-label="Initial stock" value={variant.initialStock} onChange={(e) => setVariant({ ...variant, initialStock: e.target.value })} />
            <Button type="submit">Add</Button>
          </form>
        )}
        <DataTable rows={product.variants} rowKey={(v) => v.id} columns={[
          { key: "sku", header: "SKU", cell: (v) => v.sku },
          { key: "opt", header: "Options", cell: (v) => [v.size, v.color].filter(Boolean).join(" / ") || "Default" },
          { key: "price", header: "Price", cell: (v) => formatMoney(v.effectivePrice, product.currency), hideOnMobile: true },
          { key: "stock", header: "Stock", className: "text-right", cell: (v) => (
            <span className={v.stockQuantity <= v.lowStockThreshold ? "font-medium text-destructive" : undefined}>{v.stockQuantity}</span>) },
          { key: "active", header: "Active", cell: (v) => (v.active ? "Yes" : "No"), hideOnMobile: true },
          { key: "act", header: "", className: "text-right", cell: (v) => can("INVENTORY_WRITE") && (
            <Button size="xs" variant="outline" onClick={() => { setStockFor(v.id); setChange(""); }}>Adjust stock</Button>) },
        ]} />
        <ConfirmationDialog open={stockFor !== null} onOpenChange={(o) => !o && setStockFor(null)} title="Adjust stock"
          description="Use a positive number to add stock and a negative number to remove it. Every change is logged."
          confirmLabel="Apply" onConfirm={async () => {
            const n = Number(change);
            if (!Number.isInteger(n) || n === 0) {
              toast.error("Enter a whole number other than 0");
              throw new Error("invalid");
            }
            try {
              onChange(await adminApi.adjustStock(product.id, stockFor!, { change: n, reason }));
              toast.success("Stock updated");
            } catch (e) {
              toast.error(errorMessage(e));
              throw e;
            }
          }}>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field id="change" label="Change"><Input id="change" type="number" value={change} onChange={(e) => setChange(e.target.value)} /></Field>
            <Field id="reason" label="Reason">
              <NativeSelect id="reason" value={reason} onChange={(e) => setReason(e.target.value)}>
                <option value="RESTOCK">Restock</option>
                <option value="RETURN">Customer return</option>
                <option value="ADJUSTMENT">Stock count correction</option>
                <option value="DAMAGED">Damaged / lost</option>
              </NativeSelect>
            </Field>
          </div>
        </ConfirmationDialog>
      </CardContent>
    </Card>
  );
}

function ImagesCard({ product, onChange }: { product: AdminProduct; onChange: (p: AdminProduct) => void }) {
  const { can } = useAuth();
  const [uploading, setUploading] = useState(false);
  const writable = can("CATALOG_WRITE");
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Images</CardTitle>
        {writable && (
          <label className={buttonVariants({ variant: "outline", size: "sm" })}>
            <Upload /> {uploading ? "Uploading…" : "Upload image"}
            <input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={uploading} onChange={async (e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (!file) {
                return;
              }
              if (file.size > 5 * 1024 * 1024) {
                toast.error("Images must be 5 MB or smaller");
                return;
              }
              setUploading(true);
              try {
                onChange(await adminApi.uploadImage(product.id, file, product.name));
              } catch (err) {
                toast.error(errorMessage(err));
              } finally {
                setUploading(false);
              }
            }} />
          </label>
        )}
      </CardHeader>
      <CardContent>
        {product.images.length === 0 ? <p className="text-sm text-muted-foreground">No images yet. JPEG, PNG or WebP up to 5 MB.</p> : (
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {product.images.map((img) => (
              <li key={img.id} className="space-y-2">
                <div className="relative aspect-square overflow-hidden rounded-lg border">
                  <ProductImage src={img.url} alt={img.altText ?? ""} className="size-full" />
                  {img.primary && <Badge className="absolute top-1 left-1">Primary</Badge>}
                </div>
                {writable && (
                  <div className="flex gap-1">
                    {!img.primary && <Button size="xs" variant="outline" onClick={async () => onChange(await adminApi.setPrimaryImage(product.id, img.id))}><Star /> Primary</Button>}
                    <Button size="xs" variant="ghost" aria-label="Delete image" onClick={async () => onChange(await adminApi.deleteImage(product.id, img.id))}><Trash2 /></Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
