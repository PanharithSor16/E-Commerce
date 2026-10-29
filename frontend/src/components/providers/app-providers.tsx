"use client";

import type { ReactNode } from "react";
import { Toaster } from "sonner";

import type { CurrentUser } from "@/lib/api/types";

import { AuthProvider } from "./auth-provider";
import { CartProvider } from "./cart-provider";

export function AppProviders({ user, children }: { user: CurrentUser | null; children: ReactNode }) {
  return (
    <AuthProvider initialUser={user}>
      <CartProvider>
        {children}
        <Toaster richColors position="top-right" closeButton />
      </CartProvider>
    </AuthProvider>
  );
}
