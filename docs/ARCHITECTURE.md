# Architecture

This document records the architecture and the key decisions for the e-commerce platform.
The database design is in [DATABASE.md](DATABASE.md).

## 1. System overview

```
                        HTTPS (443)
 Browser ───────────────────────────────► Reverse proxy (Nginx / Caddy)
                                           │  TLS termination, HSTS, gzip,
                                           │  sets X-Forwarded-For (trusted)
                         ┌─────────────────┴──────────────────┐
                         │ /            │ /api/*              │
                         ▼              ▼                     │
                  Next.js (3000)   Spring Boot (8080) ◄───────┘
                  App Router       REST API /api/v1
                  SSR + client     Spring Security
                         │              │
                         │  server-side │ JDBC (TLS), least-privilege login
                         │  fetch with  ▼
                         └──cookie──► SQL Server 2022 (private network only)
                                        Flyway-managed schema
```

* **One public origin.** The browser talks only to `https://shop.example.com`. The reverse proxy
  sends `/api/*` to Spring Boot and everything else to Next.js. In development, Next.js `rewrites`
  proxies `/api/*` to `http://localhost:8080`. Because the API is same-origin, the session cookie
  is first-party, `SameSite=Lax` works, and CORS is only a defence-in-depth allowlist.
* **Server components** call the backend over the internal network (`API_INTERNAL_URL`), forwarding
  the incoming `Cookie` header so the backend authorizes the real user. The backend is always the
  authority; Next.js route guards exist only for UX.
* **SQL Server is never exposed publicly.** It sits on a private Docker network / VNet.

## 2. Technology stack (pinned versions)

Versions were checked against Maven Central / npm and for mutual compatibility (springdoc 3.2.0 is
built against Spring Boot 4.1.2).

| Layer | Choice |
|---|---|
| Runtime | Java 21 (LTS) in Docker (builds locally on newer JDKs), Node.js 22 LTS |
| Backend | Spring Boot 4.1.2 (Spring Framework 7.0, Spring Security 7.1, Hibernate ORM 7.4, Jackson 3.1, Jakarta EE 11), Maven wrapper |
| API docs | springdoc-openapi 3.2.0, Swagger UI at `/swagger-ui.html` (disabled in prod by default) |
| Database | SQL Server 2022, mssql-jdbc 13.4, Flyway 12.4 + `flyway-sqlserver` (versions from the Spring Boot BOM) |
| Monitoring | Spring Boot Actuator (`health`, `info`, `prometheus` on a separate management port) |
| Frontend | Next.js 16.4 (App Router, `proxy.ts`), React 19.3, TypeScript 5 (strict), Tailwind CSS 4, shadcn/ui (Base UI primitives) |
| Frontend libs | Axios 1.21, React Hook Form 7.91, Zod 4.6, Recharts 3.10, Stripe.js (Payment Element) |
| Testing | JUnit 6, Mockito, Spring Security Test, Testcontainers 2 (SQL Server), Vitest 5 + Testing Library, Playwright 1.64 |
| Delivery | Docker multi-stage images, Docker Compose (dev + prod overlays) |

## 3. Authentication strategy (decision)

**Chosen: stateful server-side sessions referenced by an opaque token in a secure HTTP-only cookie.**

Why not JWTs: the requirements include viewing active sessions, revoking a session immediately,
session expiry and lockout. Stateless JWTs cannot be revoked without a server-side denylist, which
removes their main advantage. An opaque token checked against `AuthenticationSessions` gives
immediate revocation and a natural "active sessions" list.

| Aspect | Design |
|---|---|
| Token | 256-bit random value, Base64URL, generated with `SecureRandom` |
| Storage | Only `SHA-256(token)` is stored (`AuthenticationSessions.TokenHash`). A DB leak does not leak usable sessions. |
| Cookie | `__Host-sid` in production (`Secure; HttpOnly; SameSite=Lax; Path=/`, no `Domain`). Plain `sid` in local HTTP dev. |
| Expiry | Sliding idle timeout + absolute lifetime. Customers: idle 7 days / absolute 30 days. Staff/Admin: idle 30 min / absolute 12 h. Configurable. |
| Revocation | Logout, user "sign out other devices", admin revoke, password change, and deactivation set `RevokedAt`. Next request gets `401`. |
| CSRF | Spring Security CSRF with `CookieCsrfTokenRepository` (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header). Axios sends this header automatically for same-origin requests. All state-changing requests require it. |
| Passwords | BCrypt (strength 12). Min length 10, max 128, checked against a small common-password list. |
| Lockout | 5 failed attempts within 15 min locks the account for 15 min (`Users.LockedUntil`). Separate per-IP rate limit on `/api/v1/auth/**` (Bucket4j, in-memory; Redis-backed when scaled out). Login responses are always generic: "Invalid email or password". |
| Email verification / reset | Single-use tokens, only hashes stored (`UserTokens`). Reset tokens expire in 30 min, verification in 24 h. Reset revokes all sessions. Forgot-password always returns `202` (no account enumeration). |

Auth endpoints (the spec's `/auth/refresh` is not needed with sliding sessions; it is replaced by
`/auth/me`). Side writes that run in their own transaction (login history, analytics) are
deferred until after commit (`AfterCommit`), because a `REQUIRES_NEW` insert that references a row
the outer transaction has not committed yet would wait on it forever (an app-level deadlock SQL
Server cannot detect).

```
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET  /api/v1/auth/me
GET  /api/v1/auth/csrf
POST /api/v1/auth/verify-email
POST /api/v1/auth/resend-verification
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
GET  /api/v1/account/sessions          DELETE /api/v1/account/sessions/{publicId}
```

### Client IP handling

`server.forward-headers-strategy=NATIVE` with Tomcat's `RemoteIpValve` configured with
`internal-proxies` set to the reverse proxy's address only. Without a trusted proxy,
`X-Forwarded-For` is ignored and the socket address is used. The IP is recorded only in
`LoginHistory`, `AuthenticationSessions` and `AuditLogs` (security purposes), never in analytics.

## 4. Authorization model

Roles carry permissions (`Roles` → `RolePermissions` → `Permissions`). Spring Security authorities
contain both `ROLE_*` and permission names, and endpoints are guarded with
`@PreAuthorize("hasAuthority('ORDER_REFUND')")` rather than role names, so STAFF's scope can be
changed in data without code changes.

| Permission | CUSTOMER | STAFF | ADMIN |
|---|:-:|:-:|:-:|
| CATALOG_READ, CATALOG_WRITE, INVENTORY_WRITE | | ✓ | ✓ |
| ORDER_READ, ORDER_FULFILL | | ✓ | ✓ |
| ORDER_REFUND | | | ✓ |
| CUSTOMER_READ | | ✓ | ✓ |
| CUSTOMER_WRITE, ROLE_ASSIGN | | | ✓ |
| SECURITY_LOG_READ, SESSION_REVOKE, AUDIT_LOG_READ | | | ✓ |
| ANALYTICS_READ | | ✓ | ✓ |
| ANALYTICS_EXPORT, COUPON_WRITE, SETTINGS_WRITE | | | ✓ |

Customer-owned resources (`/api/v1/orders`, `/api/v1/account/**`, `/api/v1/cart`) are always
resolved from the authenticated principal, never from a user ID in the request. Fetching another
customer's order returns `404` (not `403`) to avoid confirming that it exists.

`/api/v1/admin/**` requires an authenticated user with at least one staff permission; each
endpoint then checks its specific permission.

## 5. Backend structure

```
backend/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── Dockerfile
└── src/
    ├── main/java/com/org/ecommerce/
    │   ├── EcommerceApplication.java
    │   ├── config/          # Jackson, OpenAPI, CORS, clock (UTC), async, properties binding
    │   ├── security/        # SecurityFilterChain, session-token filter, CSRF, rate limiting,
    │   │                    # password policy, client-IP resolver, current-user helpers
    │   ├── controller/      # REST controllers (thin: validate, delegate, map)
    │   │   └── admin/       # /api/v1/admin/** controllers
    │   ├── service/         # business logic + transactions
    │   │   └── impl/
    │   ├── repository/      # Spring Data JPA repositories + projection interfaces
    │   ├── entity/          # JPA entities (never serialized directly)
    │   ├── dto/             # request/response records, grouped by feature
    │   ├── mapper/          # entity <-> DTO mapping (MapStruct)
    │   ├── exception/       # domain exceptions + @RestControllerAdvice (ProblemDetail)
    │   ├── validation/      # custom Bean Validation constraints
    │   ├── audit/           # audit log writer, JPA auditing (CreatedAt/UpdatedAt)
    │   └── analytics/       # event ingestion, sanitizers, rollup + retention jobs
    ├── main/resources/
    │   ├── application.yml, application-dev.yml, application-prod.yml
    │   ├── logback-spring.xml
    │   └── db/migration/    # Flyway V*__*.sql
    └── test/java/com/org/ecommerce/...
```

`analytics` is added to the requested package list because ingestion, sanitizing and rollups form a
cohesive module that would otherwise be spread across `service` and `config`.

### Request lifecycle

`SessionTokenAuthenticationFilter` reads the cookie → hashes it → loads the session (with the user's
roles and permissions) → rejects revoked or expired sessions → extends `IdleExpiresAt` at most once
per minute (to avoid a write on every request) → populates `SecurityContext`. Controllers validate
DTOs with Jakarta Bean Validation; services run business rules inside `@Transactional` boundaries;
repositories use parameterized JPQL/Criteria only (no string-concatenated SQL).

### API conventions

* Base path `/api/v1`. JSON, `camelCase`. Timestamps are ISO-8601 UTC (`2026-10-28T09:15:00.000Z`).
  Money is serialized as a string decimal (`"19.9900"`) with a `currency` field, never as a float.
* Pagination: `?page=0&size=20&sort=createdAt,desc`, `size` capped at 100. Response:
  `{ "content": [...], "page": 0, "size": 20, "totalElements": 132, "totalPages": 7 }`.
* Errors: RFC 9457 `application/problem+json`:
  `{ "type", "title", "status", "detail", "instance", "errors": [{ "field", "message" }], "traceId" }`.
  No stack traces, SQL messages or class names reach clients.
* Status codes: `200/201/202/204`, `400` validation, `401` unauthenticated, `403` forbidden,
  `404` not found / not owned, `409` conflict (stock, optimistic lock, duplicate), `413`, `422`
  business-rule violation, `429` rate limited.

## 6. Frontend structure

```
frontend/
├── package.json, next.config.ts, tsconfig.json, components.json (shadcn)
├── Dockerfile
└── src/
    ├── app/
    │   ├── (storefront)/           # public layout: Header + Footer
    │   │   ├── page.tsx            # /
    │   │   ├── products/  products/[id]/  categories/[id]/  search/
    │   │   ├── cart/  checkout/
    │   │   └── privacy/
    │   ├── (auth)/                 # login, register, forgot-password, reset-password, verify-email
    │   ├── account/                # layout checks session server-side, redirects to /login
    │   │   ├── profile/  orders/  orders/[id]/  security/  sessions/
    │   └── admin/                  # layout requires staff permissions server-side
    │       ├── products/  categories/  orders/  customers/  analytics/
    │       └── login-history/  sessions/  settings/
    ├── components/
    │   ├── ui/                     # shadcn/ui primitives
    │   ├── layout/                 # Header, Footer, AdminSidebar, AdminHeader
    │   ├── product/                # ProductCard, ProductGrid, ProductFilters
    │   ├── cart/  checkout/        # ShoppingCart, CheckoutForm
    │   ├── analytics/              # AnalyticsCards, AnalyticsCharts, PageViewTracker, ConsentBanner
    │   └── common/                 # DataTable, DateRangePicker, Pagination, LoadingState,
    │                               # ErrorState, ConfirmationDialog, SearchBar
    ├── lib/
    │   ├── api/                    # axios client (browser), server fetch helper, typed endpoint modules
    │   ├── auth/                   # getCurrentUser() for server components, permission helpers
    │   ├── analytics/              # track(), consent state, dedupe
    │   └── validation/             # Zod schemas shared by forms
    └── types/                      # API request/response types
```

* Only `NEXT_PUBLIC_*` variables reach the browser, and none of them are secrets.
* `proxy.ts` (Next.js 16's name for middleware) does a cheap cookie-presence redirect for `/account`,
  `/admin` and `/checkout`; the real check happens in the layouts (server components calling
  `/api/v1/auth/me`) and, above all, in the backend.
* `cacheComponents` is turned off: nearly every page depends on the session cookie or live stock, so
  the classic dynamic rendering model is used (public pages are server-rendered per request).
* Server-rendered lists page with links (`PagerLinks`); interactive account/admin screens fetch on the
  client through the `useApi` hook and the Axios client.

## 7. Analytics design

### Flow

```
Browser (consent = granted)                       Backend
  PageViewTracker (usePathname + useSearchParams)
  track('PRODUCT_VIEW', {productId}) ──POST /api/v1/analytics/events──►  AnalyticsController
     • eventId = crypto.randomUUID()                                     • rate limit per visitor/IP
     • visitor cookie `vid` (random UUID, 13 months)                    • max body 16 KB, max 20 events/batch
     • session cookie `vsid` (30 min idle)                              • allowlist event types & property keys
     • sendBeacon / fetch keepalive, batched                            • strip query strings, truncate
                                                                         • validate product/category IDs
                                                                         • drop server-only types from client
                                                                         • INSERT, ignore duplicate EventId
  Order confirmed / payment webhook  ──────────────────────────────────► PURCHASE event (Source=SERVER,
                                                                         unique per OrderId)
```

* No consent → no visitor cookie, no events (essential cookies only: session, CSRF, cart).
* The `vid`/`vsid` cookies are set by the storefront only after consent. The backend reads them for
  server events (LOGIN, REGISTER) and stores them on the order at checkout (migration V6), so the
  webhook-driven PURCHASE event can be attributed to the right funnel session.
* Account pages (`/account/**`), checkout payment step, auth pages and `/admin/**` are excluded
  from page tracking; checkout records only `BEGIN_CHECKOUT`.
* React Strict Mode double effects are handled by tracking per `pathname+search` with a ref and a
  short-lived in-memory dedupe set; the server dedupes again by `EventId`.

### Metric definitions

All reports take a `[from, to)` range in UTC; daily buckets are UTC days.

| Metric | Definition |
|---|---|
| Page views | Count of `PAGE_VIEW` events in range. |
| Sessions | Count of `VisitorSessions` with `StartedAt` in range. A session ends after 30 min inactivity. |
| Unique visitors | `COUNT(DISTINCT VisitorId)` over events in range (cookie-based, consented browsers only; one person on two devices counts twice). Never a raw event count. |
| New visitors | Visitors with `FirstSeenAt` in range. |
| Active visitors | Distinct visitors with a session `LastActivityAt` in the last 5 minutes. |
| Registered customers | Users with `ROLE_CUSTOMER`, `CreatedAt` in range (or total). |
| Authenticated users | Distinct `UserId` with a successful login or an active `AuthenticationSession` in range. |
| Login attempts | `LoginHistory` rows in range, split by `Status`. Not the same as sessions. |
| Active sessions | `AuthenticationSessions` not revoked and not expired at query time. |
| Successful orders | Orders placed in range with `Status <> 'CANCELLED'`. |
| Paid orders | Orders with `PaidAt` in range. |
| Order value (gross) | Sum of `GrandTotal` of successful orders placed in range. |
| Paid revenue | Sum of `GrandTotal` of orders with `PaidAt` in range. |
| Refunded amount | Sum of `Refunds.Amount` with `Status='SUCCEEDED'` and `CompletedAt` in range. |
| Net revenue | Paid revenue − refunded amount. |
| Funnel step N | Distinct sessions that reached step N in range (visit → product view → add to cart → begin checkout → purchase). Conversion = step N / step 1. |
| Cart abandonment (estimate) | 1 − (sessions with PURCHASE / sessions with ADD_TO_CART), labelled as an estimate. |

Revenue numbers always come from `Orders`, `Payments` and `Refunds`, never from client events.

## 8. Order and payment consistency

* Checkout (`POST /api/v1/orders`) requires login and an `Idempotency-Key` header. A retry with the
  same key returns the original order (`UQ_Orders_User_IdempotencyKey`).
* In one transaction: load cart → re-price from catalog → validate coupon → compute shipping/tax →
  decrement stock with a conditional update (`UPDATE ... SET StockQuantity = StockQuantity - @q
  WHERE Id = @id AND StockQuantity >= @q`; 0 rows → `409 Insufficient stock`) → write
  `InventoryTransactions` → insert order, items, address snapshots → mark cart `CONVERTED`.
* Payment: the backend creates the provider payment (Stripe in prod, a `MOCK` provider in dev and
  tests) with its own idempotency key. Payment success is accepted only from the provider's signed
  webhook or a server-to-server status check, never from the browser redirect. Webhook events are
  deduplicated by `PaymentWebhookEvents (Provider, ProviderEventId)`.
* ABA PayWay: the backend signs a hosted-checkout form (HMAC-SHA512) that the browser POSTs to
  PayWay; the transaction id is the payment's idempotency key. PayWay's pushback is unsigned, so it
  only triggers a `check-transaction-2` lookup, whose result is what gets applied. Reopening the
  payment page re-signs an attempt PayWay has not seen, or closes a submitted one and starts a new
  attempt; a second successful attempt for an already-paid order is refunded automatically. The
  unpaid-order expiry job checks the provider before cancelling, and the PayWay `lifetime` is set
  so the link expires with the order.
* On payment success: order `PaymentStatus=PAID`, `PaidAt` set, `PURCHASE` analytics event inserted
  (unique per order). Unpaid orders past a timeout are cancelled and stock is restored.

## 9. Cross-cutting concerns

* **Logging:** JSON logs in prod (Logback + logstash encoder), with `traceId` in MDC and returned in
  error responses. A log filter masks `password`, `token`, `authorization`, `cookie` and card-like
  number patterns.
* **Audit:** admin and security actions (role change, deactivate, revoke session, refund, price
  change, stock adjustment) write `AuditLogs`.
* **Time:** JVM and Hibernate run in UTC (`hibernate.jdbc.time_zone=UTC`), entities use `Instant`.
* **Configuration:** all secrets come from environment variables; `.env` is git-ignored and
  `.env.example` documents every variable.

## 10. Delivery phases

| Phase | Scope |
|---|---|
| 1 | Architecture and database schema (this document, Flyway V1–V6, verified on SQL Server 2022) |
| 2 | Spring Boot project, configuration, SQL Server connection, Flyway, Actuator, OpenAPI, error handling |
| 3 | Authentication, authorization, sessions, login history, lockout |
| 4 | Products and categories (public + admin), images |
| 5 | Cart and checkout pricing |
| 6 | Orders and payment integration |
| 7 | Visitor tracking and event collection |
| 8 | Admin dashboard and analytics reports |
| 9 | Automated tests and security review |
| 10 | Docker deployment and production documentation |
