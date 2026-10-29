# Frontend (Next.js 16)

Storefront, customer account and admin dashboard. See the [root README](../README.md) for setup and
[docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) for design.

```bash
npm install
npm run dev          # http://localhost:3000, proxies /api to API_INTERNAL_URL (default http://localhost:8080)
npm test             # Vitest unit/component tests
npm run test:e2e     # Playwright (needs the backend running with the dev profile)
npm run lint && npm run typecheck
```

| Path | Purpose |
|---|---|
| `src/app/(storefront)` | Public pages: home, catalog, product, category, search, cart, checkout, payment, privacy |
| `src/app/(auth)` | Login, register, forgot/reset password, email verification |
| `src/app/account`, `src/app/admin` | Server-gated customer and staff areas |
| `src/proxy.ts` | Optimistic cookie check that redirects to login (Next.js 16 "proxy", formerly middleware) |
| `src/lib/api` | Axios client (CSRF-aware), server fetch helper, typed endpoints and DTO types |
| `src/lib/analytics` | Consent state, visitor/session ids, batched event tracker |
| `src/components` | UI building blocks (shadcn/ui on Base UI), charts (Recharts), feature components |

Environment: `API_INTERNAL_URL` (server-side only), `NEXT_PUBLIC_SITE_NAME`,
`NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY`. Never put secrets in `NEXT_PUBLIC_*` variables.
