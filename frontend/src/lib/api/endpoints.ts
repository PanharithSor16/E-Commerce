"use client";

import { api } from "./client";
import type {
  Address, AddressInput, AdminProduct, AdminSession, AuditEntry, Cart, Category, CheckoutRequest, Coupon,
  CurrentUser, CustomerDetail, CustomerSummary, DateRangeQuery, LoginHistoryEntry, LoginsReport, OrderDetail,
  OrderStatus, OrderSummary, Overview, PageResponse, PaymentStart, ProductPerformance, ProductStatus,
  ProductSummary, Quote, RepeatedFailure, SalesReport, SessionInfo, Settings, ShippingMethod, VisitorsReport,
  Funnel, Role, PaymentStatus,
} from "./types";

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data);

// ---------------------------------------------------------------- auth

export const authApi = {
  me: () => data<CurrentUser>(api.get("/auth/me")),
  login: (body: { email: string; password: string }) => data<CurrentUser>(api.post("/auth/login", body)),
  register: (body: {
    email: string; password: string; firstName: string; lastName: string; analyticsConsent: boolean; marketingConsent: boolean;
  }) => data<CurrentUser>(api.post("/auth/register", body)),
  logout: () => api.post("/auth/logout"),
  forgotPassword: (email: string) => api.post("/auth/forgot-password", { email }),
  resetPassword: (token: string, newPassword: string) => api.post("/auth/reset-password", { token, newPassword }),
  verifyEmail: (token: string) => api.post("/auth/verify-email", { token }),
  resendVerification: () => api.post("/auth/resend-verification"),
};

export const accountApi = {
  updateProfile: (body: { firstName: string; lastName: string; phoneNumber?: string }) =>
    data<CurrentUser>(api.put("/account/profile", body)),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post("/account/password", { currentPassword, newPassword }),
  sessions: () => data<SessionInfo[]>(api.get("/account/sessions")),
  revokeSession: (id: string) => api.delete(`/account/sessions/${id}`),
  revokeOtherSessions: () => api.post("/account/sessions/revoke-others"),
  addresses: () => data<Address[]>(api.get("/account/addresses")),
  addAddress: (body: { label?: string; address: AddressInput; defaultShipping: boolean; defaultBilling: boolean }) =>
    data<Address>(api.post("/account/addresses", body)),
  deleteAddress: (id: number) => api.delete(`/account/addresses/${id}`),
  consent: () => data<{ analytics: boolean; marketing: boolean; policyVersion: string }>(api.get("/account/consent")),
  updateConsent: (body: { analytics: boolean; marketing: boolean }) => api.put("/account/consent", body),
};

// ---------------------------------------------------------------- catalog & cart

export const catalogApi = {
  products: (params: Record<string, string | number | undefined>) =>
    data<PageResponse<ProductSummary>>(api.get("/products", { params })),
  categories: () => data<Category[]>(api.get("/categories")),
  shippingMethods: () => data<ShippingMethod[]>(api.get("/shipping-methods")),
};

export const cartApi = {
  get: () => data<Cart>(api.get("/cart")),
  add: (variantId: number, quantity: number) => data<Cart>(api.post("/cart/items", { variantId, quantity })),
  update: (itemId: number, quantity: number) => data<Cart>(api.put(`/cart/items/${itemId}`, { quantity })),
  remove: (itemId: number) => data<Cart>(api.delete(`/cart/items/${itemId}`)),
  applyCoupon: (code: string) => data<Cart>(api.post("/cart/coupon", { code })),
  removeCoupon: () => data<Cart>(api.delete("/cart/coupon")),
};

export const orderApi = {
  quote: (shippingMethodCode: string, couponCode?: string) =>
    data<Quote>(api.post("/checkout/quote", { shippingMethodCode, couponCode: couponCode || undefined })),
  place: (body: CheckoutRequest, idempotencyKey: string) =>
    data<OrderDetail>(api.post("/orders", body, { headers: { "Idempotency-Key": idempotencyKey } })),
  list: (page = 0, size = 10) => data<PageResponse<OrderSummary>>(api.get("/orders", { params: { page, size } })),
  get: (id: number) => data<OrderDetail>(api.get(`/orders/${id}`)),
  cancel: (id: number, reason?: string) => data<OrderDetail>(api.post(`/orders/${id}/cancel`, { reason })),
  startPayment: (id: number) => data<PaymentStart>(api.post(`/orders/${id}/payments`)),
  refreshPayment: (id: number) => data<OrderDetail>(api.post(`/orders/${id}/payments/refresh`)),
  mockConfirm: (id: number, success: boolean) =>
    data<OrderDetail>(api.post(`/orders/${id}/payments/mock-confirm`, { success })),
};

// ---------------------------------------------------------------- admin

export interface ProductUpsert {
  categoryId: number;
  name: string;
  slug?: string;
  sku: string;
  brand?: string;
  shortDescription?: string;
  description?: string;
  basePrice: string;
  discountPrice?: string | null;
  status: ProductStatus;
  featured: boolean;
  initialStock?: number;
}

export interface VariantUpsert {
  sku: string;
  size?: string;
  color?: string;
  priceOverride?: string | null;
  lowStockThreshold?: number;
  active?: boolean;
  initialStock?: number;
}

export const adminApi = {
  products: (params: { q?: string; status?: ProductStatus; categoryId?: number; page?: number; size?: number }) =>
    data<PageResponse<AdminProduct>>(api.get("/admin/products", { params })),
  product: (id: number) => data<AdminProduct>(api.get(`/admin/products/${id}`)),
  createProduct: (body: ProductUpsert) => data<AdminProduct>(api.post("/admin/products", body)),
  updateProduct: (id: number, body: ProductUpsert) => data<AdminProduct>(api.put(`/admin/products/${id}`, body)),
  archiveProduct: (id: number) => api.delete(`/admin/products/${id}`),
  addVariant: (id: number, body: VariantUpsert) => data<AdminProduct>(api.post(`/admin/products/${id}/variants`, body)),
  updateVariant: (id: number, variantId: number, body: VariantUpsert) =>
    data<AdminProduct>(api.put(`/admin/products/${id}/variants/${variantId}`, body)),
  adjustStock: (id: number, variantId: number, body: { change: number; reason: string; note?: string }) =>
    data<AdminProduct>(api.post(`/admin/products/${id}/variants/${variantId}/stock`, body)),
  uploadImage: (id: number, file: File, altText?: string) => {
    const form = new FormData();
    form.append("file", file);
    if (altText) {
      form.append("altText", altText);
    }
    return data<AdminProduct>(api.post(`/admin/products/${id}/images`, form));
  },
  setPrimaryImage: (id: number, imageId: number) =>
    data<AdminProduct>(api.patch(`/admin/products/${id}/images/${imageId}`, { primary: true })),
  deleteImage: (id: number, imageId: number) => data<AdminProduct>(api.delete(`/admin/products/${id}/images/${imageId}`)),

  categories: () => data<Category[]>(api.get("/admin/categories")),
  createCategory: (body: Omit<Category, "id" | "slug"> & { slug?: string }) => data<Category>(api.post("/admin/categories", body)),
  updateCategory: (id: number, body: Omit<Category, "id" | "slug"> & { slug?: string }) =>
    data<Category>(api.put(`/admin/categories/${id}`, body)),
  deleteCategory: (id: number) => api.delete(`/admin/categories/${id}`),

  orders: (params: { q?: string; status?: OrderStatus; paymentStatus?: PaymentStatus; from?: string; to?: string; page?: number; size?: number }) =>
    data<PageResponse<OrderSummary>>(api.get("/admin/orders", { params })),
  order: (id: number) => data<OrderDetail>(api.get(`/admin/orders/${id}`)),
  updateOrderStatus: (id: number, status: OrderStatus, note?: string) =>
    data<OrderDetail>(api.post(`/admin/orders/${id}/status`, { status, note })),
  cancelOrder: (id: number, reason?: string) => data<OrderDetail>(api.post(`/admin/orders/${id}/cancel`, { reason })),
  refundOrder: (id: number, amount: string, reason: string | undefined, key: string) =>
    data<OrderDetail>(api.post(`/admin/orders/${id}/refunds`, { amount, reason }, { headers: { "Idempotency-Key": key } })),

  users: (params: { q?: string; enabled?: boolean; role?: Role; page?: number; size?: number }) =>
    data<PageResponse<CustomerSummary>>(api.get("/admin/users", { params })),
  user: (id: number) => data<CustomerDetail>(api.get(`/admin/users/${id}`)),
  updateUser: (id: number, body: { firstName: string; lastName: string; phoneNumber?: string }) =>
    data<CustomerDetail>(api.put(`/admin/users/${id}`, body)),
  setUserEnabled: (id: number, enabled: boolean, reason?: string) =>
    data<CustomerDetail>(api.post(`/admin/users/${id}/enabled`, { enabled, reason })),
  unlockUser: (id: number) => data<CustomerDetail>(api.post(`/admin/users/${id}/unlock`)),
  setUserRoles: (id: number, roles: Role[]) => data<CustomerDetail>(api.put(`/admin/users/${id}/roles`, { roles })),
  userActivity: (id: number, page = 0) => data<PageResponse<AuditEntry>>(api.get(`/admin/users/${id}/activity`, { params: { page } })),
  userSessions: (id: number) => data<AdminSession[]>(api.get(`/admin/users/${id}/sessions`)),
  revokeAllUserSessions: (id: number) => api.post(`/admin/users/${id}/sessions/revoke-all`),

  loginHistory: (params: { userId?: number; email?: string; status?: string; ip?: string; from?: string; to?: string; page?: number; size?: number }) =>
    data<PageResponse<LoginHistoryEntry>>(api.get("/admin/login-history", { params })),
  repeatedFailures: (hours = 24, threshold = 3) =>
    data<RepeatedFailure[]>(api.get("/admin/login-history/repeated-failures", { params: { hours, threshold } })),
  sessions: (page = 0, size = 25) => data<PageResponse<AdminSession>>(api.get("/admin/sessions", { params: { page, size } })),
  revokeSession: (id: string) => api.delete(`/admin/sessions/${id}`),

  settings: () => data<Settings>(api.get("/admin/settings")),
  updateShippingMethod: (id: number, body: Record<string, unknown>) => api.put(`/admin/shipping-methods/${id}`, body),
  coupons: () => data<Coupon[]>(api.get("/admin/coupons")),
  createCoupon: (body: Record<string, unknown>) => data<Coupon>(api.post("/admin/coupons", body)),
  updateCoupon: (id: number, body: Record<string, unknown>) => data<Coupon>(api.put(`/admin/coupons/${id}`, body)),
  deactivateCoupon: (id: number) => api.delete(`/admin/coupons/${id}`),
};

export const reportsApi = {
  overview: (q: DateRangeQuery) => data<Overview>(api.get("/admin/analytics/overview", { params: q })),
  visitors: (q: DateRangeQuery) => data<VisitorsReport>(api.get("/admin/analytics/visitors", { params: q })),
  products: (q: DateRangeQuery & { page?: number; size?: number }) =>
    data<PageResponse<ProductPerformance>>(api.get("/admin/analytics/products", { params: q })),
  conversion: (q: DateRangeQuery) => data<Funnel>(api.get("/admin/analytics/conversion", { params: q })),
  sales: (q: DateRangeQuery) => data<SalesReport>(api.get("/admin/analytics/sales", { params: q })),
  logins: (q: DateRangeQuery) => data<LoginsReport>(api.get("/admin/analytics/logins", { params: q })),
  exportUrl: (report: "sales" | "products" | "visitors", q: DateRangeQuery) => {
    const params = new URLSearchParams({ report, ...(q as Record<string, string>) });
    return `/api/v1/admin/analytics/export?${params.toString()}`;
  },
};
