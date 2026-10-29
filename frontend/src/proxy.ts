import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * Optimistic check only: send visitors without a session cookie to the login page before rendering
 * protected areas. Real authorization happens in the server layouts (via /auth/me) and, above all,
 * in the backend on every API call.
 */
const SESSION_COOKIES = ["__Host-sid", "sid"];

export function proxy(request: NextRequest) {
  const hasSession = SESSION_COOKIES.some((name) => request.cookies.has(name));
  if (!hasSession) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", request.nextUrl.pathname);
    return NextResponse.redirect(login);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/account/:path*", "/admin/:path*", "/checkout/:path*"],
};
