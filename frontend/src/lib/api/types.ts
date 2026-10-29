/**
 * TypeScript mirrors of the backend DTOs. Money values are decimal strings (e.g. "19.99")
 * and timestamps are ISO-8601 UTC strings.
 */

export type Money = string;

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  traceId?: string;
  errors?: { field: string; message: string }[];
}

// ---------------------------------------------------------------- auth & account

export type Role = "ROLE_CUSTOMER" | "ROLE_STAFF" | "ROLE_ADMIN";

export type Permission =
  | "CATALOG_READ" | "CATALOG_WRITE" | "INVENTORY_WRITE" | "ORDER_READ" | "ORDER_FULFILL" | "ORDER_REFUND"
  | "CUSTOMER_READ" | "CUSTOMER_WRITE" | "ROLE_ASSIGN" | "SECURITY_LOG_READ" | "SESSION_REVOKE"
  | "ANALYTICS_READ" | "ANALYTICS_EXPORT" | "COUPON_WRITE" | "SETTINGS_WRITE" | "AUDIT_LOG_READ";

export interface CurrentUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string | null;
  emailVerified: boolean;
  roles: Role[];
  permissions: Permission[];
  createdAt: string;
}

export interface SessionInfo {
  id: string;
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
  ipAddress: string | null;
  browser: string | null;
  operatingSystem: string | null;
  deviceCategory: string | null;
  current: boolean;
}

export interface AddressInput {
  recipientName: string;
  phoneNumber?: string;
  line1: string;
  line2?: string;
  city: string;
  region?: string;
  postalCode?: string;
  countryCode: string;
}

export interface Address extends AddressInput {
  id: number;
  label: string | null;
  defaultShipping: boolean;
  defaultBilling: boolean;
}

// ---------------------------------------------------------------- catalog

export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  parentId: number | null;
  sortOrder: number;
  active: boolean;
}

export interface ProductSummary {
  id: number;
  name: string;
  slug: string;
  sku: string;
  brand: string | null;
  shortDescription: string | null;
  price: Money;
  basePrice: Money;
  discountPrice: Money | null;
  currency: string;
  imageUrl: string | null;
  categoryId: number;
  categoryName: string;
  inStock: boolean;
  featured: boolean;
  publishedAt: string | null;
}

export interface Variant {
  id: number;
  sku: string;
  size: string | null;
  color: string | null;
  price: Money;
  inStock: boolean;
  lowStock: boolean;
  availableQuantity: number;
}

export interface ProductImage {
  id: number;
  url: string;
  altText: string | null;
  sortOrder: number;
  primary: boolean;
  variantId: number | null;
}

export interface ProductDetail extends Omit<ProductSummary, "imageUrl" | "categoryId" | "categoryName" | "featured"> {
  description: string | null;
  category: Category;
  variants: Variant[];
  images: ProductImage[];
}

export interface HomeData {
  featured: ProductSummary[];
  newArrivals: ProductSummary[];
  bestSellers: ProductSummary[];
  categories: Category[];
}

export interface ShippingMethod {
  code: string;
  name: string;
  description: string | null;
  fee: Money;
  freeShippingThreshold: Money | null;
  estimatedDaysMin: number | null;
  estimatedDaysMax: number | null;
}

// ---------------------------------------------------------------- cart & orders

export interface CartItem {
  id: number;
  variantId: number;
  productId: number;
  productName: string;
  productSlug: string;
  variantDescription: string | null;
  sku: string;
  imageUrl: string | null;
  unitPrice: Money;
  quantity: number;
  lineTotal: Money;
  available: boolean;
  availableQuantity: number;
}

export interface Cart {
  id: string | null;
  items: CartItem[];
  itemCount: number;
  couponCode: string | null;
  subtotal: Money;
  discountTotal: Money;
  shippingEstimate: Money;
  taxTotal: Money;
  total: Money;
  currency: string;
  warnings: string[];
}

export interface Quote {
  subtotal: Money;
  discountTotal: Money;
  shippingTotal: Money;
  taxTotal: Money;
  grandTotal: Money;
  currency: string;
  couponCode: string | null;
  itemCount: number;
}

export type OrderStatus = "PENDING" | "CONFIRMED" | "PROCESSING" | "SHIPPED" | "DELIVERED" | "CANCELLED";
export type PaymentStatus = "PENDING" | "AUTHORIZED" | "PAID" | "FAILED" | "PARTIALLY_REFUNDED" | "REFUNDED" | "CANCELLED";

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  grandTotal: Money;
  refundedTotal: Money;
  currency: string;
  itemCount: number;
  placedAt: string;
  customerEmail: string | null;
  customerName: string | null;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  currency: string;
  subtotal: Money;
  discountTotal: Money;
  shippingTotal: Money;
  taxTotal: Money;
  grandTotal: Money;
  refundedTotal: Money;
  couponCode: string | null;
  shippingMethodName: string | null;
  customerNote: string | null;
  items: {
    id: number; productId: number; variantId: number; productName: string; variantDescription: string | null;
    sku: string; unitPrice: Money; quantity: number; discountAmount: Money; lineTotal: Money;
  }[];
  addresses: ({ type: "SHIPPING" | "BILLING" } & AddressInput)[];
  history: { statusType: string; fromStatus: string | null; toStatus: string; note: string | null; changedAt: string }[];
  payments: {
    id: number; provider: string; status: string; amount: Money; currency: string; cardBrand: string | null;
    cardLast4: string | null; failureMessage: string | null; createdAt: string; completedAt: string | null;
  }[];
  refunds: { id: number; amount: Money; reason: string | null; status: string; createdAt: string; completedAt: string | null }[];
  placedAt: string;
  paidAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  cancellable: boolean;
  customerEmail: string | null;
  customerName: string | null;
}

export interface CheckoutRequest {
  shippingAddressId?: number;
  shippingAddress?: AddressInput;
  billingSameAsShipping: boolean;
  billingAddress?: AddressInput;
  shippingMethodCode: string;
  couponCode?: string;
  note?: string;
  saveAddress: boolean;
}

export interface PaymentStart {
  paymentId: number;
  provider: "MOCK" | "STRIPE" | "PAYWAY" | string;
  status: string;
  clientSecret: string | null;
  /** Hosted-page providers (ABA PayWay): the browser POSTs these signed fields to actionUrl. */
  checkout: { actionUrl: string; fields: Record<string, string> } | null;
}

// ---------------------------------------------------------------- admin

export type ProductStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";

export interface AdminVariant {
  id: number;
  sku: string;
  size: string | null;
  color: string | null;
  priceOverride: Money | null;
  effectivePrice: Money;
  stockQuantity: number;
  lowStockThreshold: number;
  active: boolean;
}

export interface AdminProduct {
  id: number;
  name: string;
  slug: string;
  sku: string;
  brand: string | null;
  shortDescription: string | null;
  description: string | null;
  basePrice: Money;
  discountPrice: Money | null;
  currency: string;
  status: ProductStatus;
  featured: boolean;
  soldCount: number;
  categoryId: number;
  categoryName: string;
  variants: AdminVariant[];
  images: ProductImage[];
  totalStock: number;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface CustomerSummary {
  id: number;
  publicId: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  emailVerified: boolean;
  locked: boolean;
  roles: Role[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface CustomerDetail extends CustomerSummary {
  phoneNumber: string | null;
  lockedUntil: string | null;
  failedLoginCount: number;
  deactivatedAt: string | null;
  orderCount: number;
  activeSessions: number;
}

export interface LoginHistoryEntry {
  id: number;
  userId: number | null;
  emailMasked: string;
  status: "SUCCESS" | "FAILURE";
  failureReason: string | null;
  occurredAt: string;
  ipAddress: string | null;
  browser: string | null;
  browserVersion: string | null;
  operatingSystem: string | null;
  deviceCategory: string | null;
  sessionId: string | null;
}

export interface RepeatedFailure {
  emailHash: string;
  emailMasked: string;
  userId: number | null;
  failures: number;
  lastAttemptAt: string;
  distinctIps: number;
}

export interface AdminSession {
  id: string;
  userId: number;
  userEmail: string;
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
  ipAddress: string | null;
  browser: string | null;
  operatingSystem: string | null;
  deviceCategory: string | null;
}

export interface AuditEntry {
  id: number;
  actorUserId: number | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  outcome: string;
  details: string | null;
  ipAddress: string | null;
  occurredAt: string;
}

export type DiscountType = "PERCENTAGE" | "FIXED_AMOUNT" | "FREE_SHIPPING";

export interface Coupon {
  id: number;
  code: string;
  description: string | null;
  discountType: DiscountType;
  discountValue: Money;
  minOrderAmount: Money | null;
  maxDiscountAmount: Money | null;
  startsAt: string | null;
  endsAt: string | null;
  usageLimit: number | null;
  perUserLimit: number | null;
  timesUsed: number;
  active: boolean;
}

export interface ShippingMethodAdmin extends ShippingMethod {
  id: number;
  active: boolean;
}

export interface Settings {
  paymentProvider: string;
  currency: string;
  taxRate: Money;
  requireVerifiedEmailForCheckout: boolean;
  privacyPolicyVersion: string;
  shippingMethods: ShippingMethodAdmin[];
}

// ---------------------------------------------------------------- analytics reports

export interface MoneyBreakdown {
  grossOrderValue: Money;
  paidRevenue: Money;
  refunded: Money;
  netRevenue: Money;
}

export interface Kpis {
  money: MoneyBreakdown;
  ordersPlaced: number;
  paidOrders: number;
  averageOrderValue: Money;
  newCustomers: number;
  uniqueVisitors: number;
  sessions: number;
  pageViews: number;
  productViews: number;
  conversionRate: number;
}

export interface NamedCount {
  name: string;
  count: number;
}

export interface FunnelStep {
  step: string;
  sessions: number;
  rateFromStart: number;
  rateFromPrevious: number;
}

export interface Funnel {
  steps: FunnelStep[];
  conversionRate: number;
  cartAbandonmentEstimate: number;
}

export interface Overview {
  range: { from: string; to: string };
  current: Kpis;
  previous: Kpis;
  totalCustomers: number;
  activeVisitors: number;
  activeSessions: number;
  trend: { date: string; revenue: Money; orders: number }[];
  bestSellers: { productId: number; name: string; unitsSold: number; revenue: Money }[];
  lowStock: { productId: number; productName: string; variantId: number; sku: string; variant: string | null; stockQuantity: number; lowStockThreshold: number }[];
  recentOrders: OrderSummary[];
  funnel: Funnel;
  devices: NamedCount[];
}

export interface VisitorsReport {
  range: { from: string; to: string };
  uniqueVisitors: number;
  sessions: number;
  pageViews: number;
  newVisitors: number;
  daily: { date: string; pageViews: number; uniqueVisitors: number; sessions: number; newVisitors: number }[];
  trafficSources: NamedCount[];
  devices: NamedCount[];
  browsers: NamedCount[];
  topPages: NamedCount[];
  campaigns: NamedCount[];
  referrers: NamedCount[];
}

export interface ProductPerformance {
  productId: number;
  name: string;
  sku: string;
  views: number;
  uniqueViewers: number;
  addToCarts: number;
  unitsSold: number;
  revenue: Money;
  viewToPurchaseRate: number;
}

export interface SalesReport {
  range: { from: string; to: string };
  money: MoneyBreakdown;
  ordersPlaced: number;
  paidOrders: number;
  averageOrderValue: Money;
  daily: { date: string; grossOrderValue: Money; paidRevenue: Money; refunded: Money; orders: number; paidOrders: number }[];
  ordersByStatus: NamedCount[];
  topCategories: { productId: number; name: string; unitsSold: number; revenue: Money }[];
}

export interface LoginsReport {
  range: { from: string; to: string };
  successes: number;
  failures: number;
  uniqueUsers: number;
  activeSessions: number;
  daily: { date: string; successes: number; failures: number }[];
  failureReasons: NamedCount[];
  devices: NamedCount[];
  repeatedFailures: RepeatedFailure[];
}

export interface DateRangeQuery {
  range?: "today" | "7d" | "30d" | "this_month" | "last_month";
  from?: string;
  to?: string;
}
