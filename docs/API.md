# API guide

The full, always-current reference is the OpenAPI document: run the backend in the `dev` profile and open
<http://localhost:8080/swagger-ui.html> (JSON at `/v3/api-docs`). This page explains the conventions and gives
working examples.

## Conventions

- Base path `/api/v1`, JSON, camelCase. Money is a decimal **string** (`"19.99"`); timestamps are UTC ISO-8601.
- Pagination: `?page=0&size=20` → `{ content, page, size, totalElements, totalPages }` (size ≤ 100).
- Errors are RFC 9457 problem details: `{ status, title, detail, code, errors?: [{field, message}], traceId }`.
- Status codes: 400 validation · 401 not signed in · 403 forbidden / bad CSRF · 404 not found (also for other
  customers' resources) · 409 conflict (stock, duplicate) · 422 business rule · 429 rate limited.

## Authentication & CSRF

Sessions use an HTTP-only cookie. Every POST/PUT/PATCH/DELETE must send the `X-XSRF-TOKEN` header with the value of
the `XSRF-TOKEN` cookie (exceptions: analytics events and payment webhooks).

```bash
B=http://localhost:8080/api/v1
curl -s -c jar -b jar $B/auth/csrf                                   # sets XSRF-TOKEN
X=$(awk '/XSRF-TOKEN/{print $7}' jar)

curl -s -c jar -b jar -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"email":"ann@example.com","password":"correct horse battery","firstName":"Ann","lastName":"Lee"}' \
  $B/auth/register                                                    # 201 + session cookie

curl -s -c jar -b jar -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"email":"ann@example.com","password":"correct horse battery"}' $B/auth/login
curl -s -b jar $B/auth/me
curl -s -b jar -c jar -X POST -H "X-XSRF-TOKEN: $X" $B/auth/logout   # 204
```

| Endpoint | Purpose |
|---|---|
| `POST /auth/register`, `/auth/login`, `/auth/logout` | Account and session |
| `GET /auth/me`, `GET /auth/csrf` | Current user, CSRF token |
| `POST /auth/forgot-password` (always 202), `/auth/reset-password`, `/auth/verify-email`, `/auth/resend-verification` | Email flows |
| `GET /account/sessions`, `DELETE /account/sessions/{id}`, `POST /account/sessions/revoke-others` | Session management |
| `PUT /account/profile`, `POST /account/password`, `/account/addresses`, `/account/consent` | Profile |

## Catalog, cart, checkout

```bash
curl -s "$B/products?q=shoe&categoryId=1&minPrice=50&maxPrice=150&sort=price_asc&page=0&size=12"
curl -s $B/products/1
curl -s $B/categories ; curl -s $B/categories/1/products ; curl -s $B/shipping-methods

curl -s -b jar -c jar -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"variantId":2,"quantity":2}' $B/cart/items                    # works anonymously (cart cookie) too
curl -s -b jar -c jar -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' -d '{"code":"WELCOME10"}' $B/cart/coupon

curl -s -b jar -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"shippingMethodCode":"STANDARD"}' $B/checkout/quote

curl -s -b jar -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"shippingAddress":{"recipientName":"Ann Lee","line1":"1 Main St","city":"Phnom Penh","countryCode":"KH"},
       "billingSameAsShipping":true,"shippingMethodCode":"STANDARD","saveAddress":true}' $B/orders   # 201

curl -s -b jar -X POST -H "X-XSRF-TOKEN: $X" $B/orders/1/payments                           # start payment
curl -s -b jar -X POST -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"success":true}' $B/orders/1/payments/mock-confirm                                    # MOCK provider only
curl -s -b jar "$B/orders?page=0" ; curl -s -b jar $B/orders/1
```

Prices, discounts, shipping, tax and payment status are never accepted from the client; unknown request fields are
ignored. Retrying `POST /orders` with the same `Idempotency-Key` returns the original order.

## Analytics ingestion

```bash
curl -s -H 'Content-Type: application/json' -d '{
  "visitorId":"6d7c2a1e-2b3f-4c5d-8e9f-0a1b2c3d4e5f","sessionId":"0f1e2d3c-4b5a-4968-8776-5544332211aa",
  "events":[{"eventId":"'$(uuidgen)'","type":"PRODUCT_VIEW","path":"/products/1","productId":1}]}' \
  $B/analytics/events          # 202 {"accepted":1,"rejected":0}
```

At most 20 events / 16 KB per request. Server-only types (`PURCHASE`, `LOGIN`, `LOGOUT`, `REGISTER`), excluded
paths (`/account`, `/admin`, auth pages, payment step) and unknown product ids are rejected.

## Admin (requires staff permissions)

| Endpoint | Permission |
|---|---|
| `GET/POST/PUT/DELETE /admin/products…`, `/admin/categories…`, images (multipart `file`) | `CATALOG_READ` / `CATALOG_WRITE` |
| `POST /admin/products/{id}/variants/{vid}/stock` `{change, reason, note}` | `INVENTORY_WRITE` |
| `GET /admin/orders`, `GET /admin/orders/{id}` | `ORDER_READ` |
| `POST /admin/orders/{id}/status` `{status, note}` | `ORDER_FULFILL` |
| `POST /admin/orders/{id}/cancel`, `POST /admin/orders/{id}/refunds` (+`Idempotency-Key`) | `ORDER_REFUND` |
| `GET/PUT /admin/users…`, `POST /admin/users/{id}/enabled`, `/unlock` | `CUSTOMER_READ` / `CUSTOMER_WRITE` |
| `PUT /admin/users/{id}/roles` | `ROLE_ASSIGN` |
| `GET /admin/login-history`, `/admin/login-history/{id}`, `/admin/login-history/repeated-failures`, `GET /admin/sessions`, `/admin/users/{id}/sessions` | `SECURITY_LOG_READ` |
| `DELETE /admin/sessions/{id}`, `POST /admin/users/{id}/sessions/revoke-all` | `SESSION_REVOKE` |
| `GET /admin/analytics/{overview,visitors,products,conversion,sales}` | `ANALYTICS_READ` |
| `GET /admin/analytics/logins` | `SECURITY_LOG_READ` |
| `GET /admin/analytics/export?report=sales\|products\|visitors` | `ANALYTICS_EXPORT` |
| `/admin/coupons…` · `PUT /admin/shipping-methods/{id}` · `GET /admin/audit-logs` | `COUPON_WRITE` · `SETTINGS_WRITE` · `AUDIT_LOG_READ` |

Report parameters: `range=today|7d|30d|this_month|last_month` or `from=YYYY-MM-DD&to=YYYY-MM-DD` (inclusive, UTC,
max 366 days).
