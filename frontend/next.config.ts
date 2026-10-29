import type { NextConfig } from "next";

/**
 * The browser only ever talks to this origin. `/api/*` is forwarded to Spring Boot so the
 * session cookie stays first-party (SameSite=Lax) and no CORS is needed. In production the
 * reverse proxy routes `/api/*` to the backend directly; this rewrite is the dev/fallback path.
 */
const apiUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  // Every page here depends on the session or live stock, so the classic dynamic rendering model is used.
  cacheComponents: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  images: {
    // Product images are served by the API under /api/v1/media (same origin).
    remotePatterns: [],
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/api/:path*` }];
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
