# Deployment & operations

## Topology

```
Internet ──443──► nginx (TLS, HSTS, edge rate limit)
                    ├─ /api/*  ──► backend  :8080   (Spring Boot, prod profile)
                    └─ /*      ──► frontend :3000   (Next.js standalone)
backend ──► sqlserver :1433 on the internal "data" network (no host port, no internet route)
backend ──► SMTP relay, Stripe API
```

Only nginx publishes ports. Actuator runs on the management port 8081 and is not routed by nginx.

## Environments

| | Development | Production |
|---|---|---|
| Spring profile | `dev` (default) | `prod` |
| Session cookie | `sid`, not Secure (HTTP) | `__Host-sid`, Secure, HTTPS only |
| Swagger UI | enabled | disabled (`APP_API_DOCS_ENABLED=false`) |
| Logs | human-readable, DEBUG for app | JSON (ECS), INFO |
| DB logins | `sa` | `ecommerce_migrator` (Flyway) + `ecommerce_app` (runtime, DML only) |
| Seed data | `db/seed` demo catalog | none |
| Payments | `MOCK` | `STRIPE` or `PAYWAY` (ABA PayWay) |
| Email | Mailpit | real SMTP relay |

## First deployment

1. Provision a Linux host with Docker Engine 26+ and Compose v2.24+. Point DNS for your hostname at it.
2. Clone the repository and create `.env` from `.env.example`. Generate every secret:
   ```bash
   openssl rand -base64 32   # APP_SECURITY_EMAIL_HASH_KEY
   openssl rand -base64 24   # passwords (must satisfy SQL Server complexity rules)
   ```
   Set `APP_PUBLIC_BASE_URL=https://shop.example.com`, `PUBLIC_HOSTNAME`, SMTP settings, Stripe keys and
   `APP_BOOTSTRAP_ADMIN_*`.
3. Obtain certificates (e.g. certbot standalone, before starting nginx) into `infra/certs/fullchain.pem` and
   `infra/certs/privkey.pem`, or point `TLS_CERT_DIR` at the certbot live directory.
4. Start:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   docker compose logs -f backend   # wait for "Started EcommerceApplication"
   ```
   `db-init` creates the database and logins; the backend runs Flyway as `ecommerce_migrator`
   and serves traffic as `ecommerce_app`.
5. Sign in with the bootstrap admin, change the password, then **remove**
   `APP_BOOTSTRAP_ADMIN_PASSWORD` from `.env` and restart the backend.
6. Stripe: create a webhook endpoint `https://shop.example.com/api/v1/payments/webhooks/stripe` for
   `payment_intent.succeeded` and `payment_intent.payment_failed`; put its signing secret in
   `STRIPE_WEBHOOK_SECRET`. Set `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` and rebuild the frontend image.

   ABA PayWay instead: set `PAYMENT_PROVIDER=PAYWAY`, `PAYWAY_MERCHANT_ID`, `PAYWAY_API_KEY` and
   `PAYWAY_BASE_URL=https://checkout.payway.com.kh` (production; the default is the sandbox). Ask ABA
   to whitelist the server's public IP and domain. The pushback URL sent to PayWay is
   `https://shop.example.com/api/v1/payments/webhooks/payway` (override with `PAYWAY_CALLBACK_URL`).
   Refunds additionally need ABA's RSA public key in `PAYWAY_RSA_PUBLIC_KEY` (PEM; `
` escapes are
   accepted). No frontend rebuild is needed: the customer is sent to PayWay's hosted checkout page.

## Updating

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build backend frontend
```

Flyway applies new migrations on backend start. Migrations are additive; never edit an applied one.
For zero-downtime releases run two backend replicas behind nginx and keep migrations
backward-compatible (expand, deploy, contract).

## Scaling notes

- The backend is stateless apart from two in-memory concerns: the rate limiter (`RateLimiter`) and
  product images on the `uploads` volume. Before running several replicas, move rate limiting to Redis
  (or rely on the nginx `limit_req` zone) and put images on shared/object storage.
- Scheduled jobs (unpaid-order expiry, nightly analytics rollup and retention) are idempotent, but with
  several replicas add ShedLock or run them on one instance.

## Monitoring & logging

- Health: `GET :8081/actuator/health/liveness` and `/readiness` (used by the Docker healthcheck).
- Metrics: `GET :8081/actuator/prometheus`; scrape it from inside the Docker network.
- Logs: JSON on stdout, each line carries `traceId`; the same id is returned as `X-Request-Id` and in
  error bodies, so a customer-reported error can be found in logs. Ship container logs with your
  collector (Loki, ELK, CloudWatch).
- Alert on: readiness failures, 5xx rate, `ACCOUNT_LOCKED` audit spikes, repeated-failure counts in
  `/admin/login-history`, payment webhook `FAILED` rows, disk usage of the SQL Server volume.

## Backup & restore

SQL Server runs in `FULL` recovery. Recommended schedule: full daily, differential every 6 h,
transaction log every 15 min, copied off-host, encrypted, and restore-tested monthly.

```bash
# Full backup (writes to ./backups on the host via the mounted volume)
docker compose exec sqlserver /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -Q \
  "BACKUP DATABASE ecommerce TO DISK='/var/opt/mssql/backup/ecommerce_full.bak' WITH COMPRESSION, CHECKSUM, INIT"

# Log backup
docker compose exec sqlserver /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -Q \
  "BACKUP LOG ecommerce TO DISK='/var/opt/mssql/backup/ecommerce_log.trn' WITH COMPRESSION, CHECKSUM"

# Restore (stop the backend first)
docker compose stop backend
docker compose exec sqlserver /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -Q \
  "ALTER DATABASE ecommerce SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
   RESTORE DATABASE ecommerce FROM DISK='/var/opt/mssql/backup/ecommerce_full.bak' WITH REPLACE, CHECKSUM;
   ALTER DATABASE ecommerce SET MULTI_USER;"
docker compose start backend
```

Back up the `uploads` volume (product images) alongside the database.

## Security checklist

- [ ] `.env` is not committed; secrets rotated from the examples.
- [ ] HTTPS only; HSTS enabled; `APP_SECURITY_COOKIE_SECURE` true (prod profile).
- [ ] SQL Server has no published port; `sa` password stored in a secrets manager; app uses `ecommerce_app`.
- [ ] Swagger disabled publicly; actuator not routed.
- [ ] `APP_TRUSTED_PROXIES_REGEX` matches only the reverse proxy.
- [ ] Bootstrap admin password removed after first login.
- [ ] Dependency scan in CI: `./mvnw -Psecurity verify` (OWASP, set `NVD_API_KEY`) and `npm audit --omit=dev`.
- [ ] Backups restore-tested.
