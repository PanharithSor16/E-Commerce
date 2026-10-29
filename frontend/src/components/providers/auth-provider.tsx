"use client";

import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";

import { authApi } from "@/lib/api/endpoints";
import type { CurrentUser, Permission } from "@/lib/api/types";

interface AuthContextValue {
  user: CurrentUser | null;
  setUser: (user: CurrentUser | null) => void;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
  can: (permission: Permission) => boolean;
  isStaff: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Client-side view of the session, seeded by the server layout (which asked the backend). */
export function AuthProvider({ initialUser, children }: { initialUser: CurrentUser | null; children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(initialUser);

  const refresh = useCallback(async () => {
    try {
      setUser(await authApi.me());
    } catch {
      setUser(null);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
    }
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    setUser,
    refresh,
    logout,
    can: (p) => !!user?.permissions.includes(p),
    isStaff: !!user && (user.roles.includes("ROLE_ADMIN") || user.roles.includes("ROLE_STAFF")),
  }), [user, refresh, logout]);

  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return ctx;
}
