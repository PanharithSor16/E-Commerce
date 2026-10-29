package com.org.ecommerce.entity;

/** Enumerations persisted as strings; values must match the CHECK constraints in the migrations. */
public final class Enums {

    private Enums() {
    }

    public enum TokenType { EMAIL_VERIFICATION, PASSWORD_RESET }

    public enum RevokedReason { LOGOUT, USER_REVOKED, ADMIN_REVOKED, PASSWORD_CHANGED, ACCOUNT_DEACTIVATED, EXPIRED }

    public enum DeviceCategory { DESKTOP, TABLET, MOBILE, BOT, UNKNOWN }

    public enum LoginStatus { SUCCESS, FAILURE }

    public enum LoginFailureReason {
        BAD_CREDENTIALS, UNKNOWN_ACCOUNT, ACCOUNT_LOCKED, ACCOUNT_DISABLED, EMAIL_NOT_VERIFIED, RATE_LIMITED, OTHER
    }

    public enum AuditOutcome { SUCCESS, FAILURE, DENIED }

    public enum ProductStatus { DRAFT, ACTIVE, ARCHIVED }

    public enum DiscountType { PERCENTAGE, FIXED_AMOUNT, FREE_SHIPPING }

    public enum CartStatus { ACTIVE, CONVERTED, MERGED, ABANDONED }

    public enum OrderStatus { PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED }

    public enum PaymentStatus { PENDING, AUTHORIZED, PAID, FAILED, PARTIALLY_REFUNDED, REFUNDED, CANCELLED }

    public enum AddressType { SHIPPING, BILLING }

    public enum StatusType { FULFILLMENT, PAYMENT }

    public enum PaymentTxStatus { PENDING, REQUIRES_ACTION, AUTHORIZED, SUCCEEDED, FAILED, CANCELLED }

    public enum RefundStatus { PENDING, SUCCEEDED, FAILED }

    public enum WebhookStatus { RECEIVED, PROCESSED, IGNORED, FAILED }

    public enum InventoryReason { INITIAL, RESTOCK, SALE, ORDER_CANCELLED, RETURN, ADJUSTMENT, DAMAGED }

    public enum EventType {
        PAGE_VIEW, PRODUCT_VIEW, CATEGORY_VIEW, SEARCH, ADD_TO_CART, REMOVE_FROM_CART, BEGIN_CHECKOUT,
        PURCHASE, LOGIN, LOGOUT, REGISTER;

        /** Events that only trusted backend workflows may record. */
        public boolean serverOnly() {
            return this == PURCHASE || this == LOGIN || this == LOGOUT || this == REGISTER;
        }
    }

    public enum EventSource { CLIENT, SERVER }

    public enum TrafficSource { DIRECT, ORGANIC, REFERRAL, SOCIAL, EMAIL, PAID, CAMPAIGN, OTHER }

    public enum ConsentType { ANALYTICS, MARKETING }

    public enum ConsentSource { BANNER, ACCOUNT_SETTINGS, REGISTRATION }
}
