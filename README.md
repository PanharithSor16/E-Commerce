# E-Commerce Platform

Full-stack e-commerce platform: a Next.js storefront, customer account area and admin dashboard,
backed by a Spring Boot REST API on SQL Server. It includes login monitoring, consent-aware visitor
analytics, a conversion funnel, inventory-safe checkout, and payments through Stripe or ABA PayWay (with a mock
provider for development).

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, auth strategy, permissions, analytics definitions, order/payment consistency |
| [docs/DATABASE.md](docs/DATABASE.md) | Schema, constraints, indexes, retention, database accounts |
| [docs/API.md](docs/API.md) | API conventions and sample requests (full reference in Swagger UI) |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Production deployment, TLS, monitoring, backup/restore, security checklist |

## Repository layout

```
e-commerce/
├── backend/                 Spring Boot 4.1 API (Java 21, Maven wrapper)
│   └── src/main/java/com/org/ecommerce/
│       config/ security/ controller/ service/ repository/ entity/ dto/ mapper/
│       exception/ validation/ audit/ analytics/
│   └── src/main/resources/db/migration/   Flyway V1–V6      db/seed/   dev-only demo data
├── frontend/                Next.js 16 (App Router), Tailwind 4, shadcn/ui
│   └── src/app/(storefront) (auth) account/ admin/      e2e/   Playwright specs
├── infra/                   nginx (HTTP + TLS), SQL Server provisioning script
├── docker-compose.yml       full stack;  docker-compose.prod.yml  production overlay (TLS)
└── .env.example
```

## Quick start with Docker (whole stack)

```bash
cp .env.example .env          # set passwords and APP_SECURITY_EMAIL_HASH_KEY (openssl rand -base64 32)
docker compose up -d --build
```

- Store: <http://localhost>. Sign in with `APP_BOOTSTRAP_ADMIN_EMAIL` / `APP_BOOTSTRAP_ADMIN_PASSWORD` for `/admin`.
- Emails (verification, password reset, order confirmation): Mailpit at <http://localhost:8025>.
- The stack runs the **prod** profile with the MOCK payment provider and an empty catalog: create
  categories and products in the admin area.

## Local development

Prerequisites: JDK 21+, Node.js 22+, Docker (for SQL Server and the integration tests).

```bash
# 1. SQL Server (port 14333 so it can't clash with a locally installed SQL Server on 1433)
docker run -d --name ecom-dev-mssql -p 127.0.0.1:14333:1433 \
  -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=Dev_Str0ng!Passw0rd' mcr.microsoft.com/mssql/server:2022-latest
docker exec ecom-dev-mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P 'Dev_Str0ng!Passw0rd' \
  -Q "CREATE DATABASE ecommerce; ALTER DATABASE ecommerce SET READ_COMMITTED_SNAPSHOT ON"

# 2. Backend (dev profile: Swagger UI, demo catalog, admin@example.com / Admin_Passw0rd!)
cd backend
DB_PORT=14333 ./mvnw spring-boot:run          # http://localhost:8080/swagger-ui.html

# 3. Frontend (proxies /api to :8080)
cd frontend
npm install
npm run dev                                   # http://localhost:3000
```

Dev profile notes: the demo catalog includes coupons `WELCOME10` and `FREESHIP`; emails go to
`localhost:1025` (run `docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit` to read them);
checkout does not require a verified email; payments use the MOCK provider ("Pay now (test card)"
or "Simulate declined card" on the payment step).

Alternative without a local database: `./mvnw spring-boot:test-run` starts the backend against a
throwaway Testcontainers SQL Server.

## Tests

| Suite | Command | What it covers |
|---|---|---|
| Backend unit + integration (64) | `cd backend && ./mvnw test` | Pricing, coupons (Mockito), sessions, webhook signatures, analytics sanitizing; Spring Boot + real SQL Server via Testcontainers: login success/failure, lockout, session expiry and revocation, CSRF, validation, role/permission enforcement, another customer's order (404), invalid product, insufficient stock, invalid coupon, idempotent checkout, payment failure → success, duplicate purchase and duplicate analytics events, concurrent stock reservation |
| Frontend unit/component (21) | `cd frontend && npm test` | Zod schemas, consent-aware tracker (no events without consent, no query strings, dedupe), ProductCard, CartSummary, Pagination, LoginForm validation and safe redirects |
| End-to-end (5) | `cd frontend && npm run test:e2e` | Registration, login, protected routes, anonymous cart → register → checkout → declined → paid, mobile navigation. Needs backend (dev profile) and frontend running. |
| Static checks | `npm run lint && npm run typecheck` | ESLint (incl. React Compiler rules), TypeScript strict |
| Dependency audit | `./mvnw -Psecurity verify` · `npm audit --omit=dev` | OWASP dependency-check (set `NVD_API_KEY`) |

The integration tests need Docker running. Playwright uses the installed Chrome by default; on CI set
`E2E_BROWSER_CHANNEL=""` and run `npx playwright install chromium`.

## Status

All ten phases are implemented. The verification below was run during development:

- Flyway V1–V6 apply cleanly to SQL Server 2022; Hibernate `ddl-auto=validate` passes.
- 64 backend tests, 21 frontend tests and 5 Playwright journeys pass.
- `next build`, ESLint and `tsc` are clean; both Docker images build; the Compose stack starts healthy
  (db-init, Flyway as the migrator login, nginx routing, `__Host-` session cookie, actuator and Swagger
  not exposed).

Known limitations and next steps:

- Stripe was implemented against its documented REST API and webhook signature scheme, and the
  signature check is unit-tested, but it has not been exercised against a live Stripe account.
- ABA PayWay (`PAYMENT_PROVIDER=PAYWAY`) was implemented against PayWay's REST API; request hashes
  are unit-tested against an independent reference implementation, but it has not been exercised
  against the PayWay sandbox yet. Refunds need `PAYWAY_RSA_PUBLIC_KEY`.
- Rate limiting is in-memory (single instance). Use Redis or the nginx `limit_req` zone when running
  several backend replicas, and add ShedLock for the scheduled jobs.
- Product images are stored on a local volume; move them to object storage for multi-instance setups.
- Tax is a single configurable flat rate (`APP_TAX_RATE`).
