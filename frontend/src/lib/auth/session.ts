import "server-only";

import { redirect } from "next/navigation";
import { cache } from "react";

import { ServerApiError, serverFetch } from "@/lib/api/server";
import type { CurrentUser, Permission } from "@/lib/api/types";

/**
 * Data Access Layer for the session. Asks the backend who the user is (the session cookie is
 * HTTP-only and opaque to Next.js). Memoized per request with React cache().
 */
export const getCurrentUser = cache(async (): Promise<CurrentUser | null> => {
  try {
    return await serverFetch<CurrentUser>("/auth/me", { withSession: true });
  } catch (e) {
    if (e instanceof ServerApiError && (e.status === 401 || e.status === 403)) {
      return null;
    }
    throw e;
  }
});

export async function requireUser(returnTo: string): Promise<CurrentUser> {
  const user = await getCurrentUser();
  if (!user) {
    redirect(`/login?next=${encodeURIComponent(returnTo)}`);
  }
  return user;
}

export function isStaff(user: CurrentUser | null): boolean {
  return !!user && (user.roles.includes("ROLE_ADMIN") || user.roles.includes("ROLE_STAFF"));
}

/** UX gate for /admin. The backend enforces every permission again on each API call. */
export async function requireStaff(): Promise<CurrentUser> {
  const user = await requireUser("/admin");
  if (!isStaff(user)) {
    redirect("/");
  }
  return user;
}

export function can(user: CurrentUser | null, permission: Permission): boolean {
  return !!user && user.permissions.includes(permission);
}
