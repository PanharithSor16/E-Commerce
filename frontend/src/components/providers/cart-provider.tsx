"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { toast } from "sonner";

import { errorMessage } from "@/lib/api/client";
import { cartApi } from "@/lib/api/endpoints";
import type { Cart } from "@/lib/api/types";
import { track } from "@/lib/analytics/track";

import { useAuth } from "./auth-provider";

interface CartContextValue {
  cart: Cart | null;
  loading: boolean;
  refresh: () => Promise<void>;
  add: (variantId: number, quantity: number, productId?: number) => Promise<boolean>;
  update: (itemId: number, quantity: number) => Promise<void>;
  remove: (itemId: number) => Promise<void>;
  applyCoupon: (code: string) => Promise<boolean>;
  removeCoupon: () => Promise<void>;
}

const CartContext = createContext<CartContextValue | null>(null);

/** Cart state shared by the header indicator, cart page and checkout. All totals come from the server. */
export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setCart(await cartApi.get());
    } catch {
      setCart(null);
    } finally {
      setLoading(false);
    }
  }, []);

  // Reload when the signed-in user changes (the anonymous cart is merged on login).
  useEffect(() => {
    let active = true;
    cartApi.get()
      .then((c) => active && setCart(c), () => active && setCart(null))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [user?.id]);

  const add = useCallback(async (variantId: number, quantity: number, productId?: number) => {
    try {
      setCart(await cartApi.add(variantId, quantity));
      track("ADD_TO_CART", { productId, variantId, quantity });
      toast.success("Added to cart");
      return true;
    } catch (e) {
      toast.error(errorMessage(e));
      return false;
    }
  }, []);

  const update = useCallback(async (itemId: number, quantity: number) => {
    try {
      setCart(await cartApi.update(itemId, quantity));
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }, []);

  const remove = useCallback(async (itemId: number) => {
    const item = cart?.items.find((i) => i.id === itemId);
    try {
      setCart(await cartApi.remove(itemId));
      if (item) {
        track("REMOVE_FROM_CART", { productId: item.productId, variantId: item.variantId, quantity: item.quantity });
      }
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }, [cart]);

  const applyCoupon = useCallback(async (code: string) => {
    try {
      setCart(await cartApi.applyCoupon(code));
      toast.success("Coupon applied");
      return true;
    } catch (e) {
      toast.error(errorMessage(e));
      return false;
    }
  }, []);

  const removeCoupon = useCallback(async () => {
    try {
      setCart(await cartApi.removeCoupon());
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }, []);

  const value = useMemo(() => ({ cart, loading, refresh, add, update, remove, applyCoupon, removeCoupon }),
    [cart, loading, refresh, add, update, remove, applyCoupon, removeCoupon]);
  return <CartContext value={value}>{children}</CartContext>;
}

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext);
  if (!ctx) {
    throw new Error("useCart must be used inside CartProvider");
  }
  return ctx;
}
