"use client";

import { LayoutDashboard, LogOut, Menu, Package, ShoppingBag, ShoppingCart, User } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { SearchBar } from "@/components/common/search-bar";
import { useAuth } from "@/components/providers/auth-provider";
import { useCart } from "@/components/providers/cart-provider";
import { buttonVariants } from "@/components/ui/button";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import type { Category } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const SITE_NAME = process.env.NEXT_PUBLIC_SITE_NAME ?? "Shop";

export function Header({ categories }: { categories: Category[] }) {
  const topLevel = categories.filter((c) => c.parentId === null).slice(0, 6);
  return (
    <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4">
        <MobileNav categories={topLevel} />
        <Link href="/" className="flex items-center gap-2 font-semibold">
          <ShoppingBag className="size-5" aria-hidden />
          <span>{SITE_NAME}</span>
        </Link>
        <nav aria-label="Categories" className="ml-4 hidden items-center gap-4 text-sm lg:flex">
          <Link href="/products" className="text-muted-foreground hover:text-foreground">All products</Link>
          {topLevel.map((c) => (
            <Link key={c.id} href={`/categories/${c.id}`} className="text-muted-foreground hover:text-foreground">{c.name}</Link>
          ))}
        </nav>
        <div className="ml-auto hidden w-full max-w-xs md:block">
          <SearchBar />
        </div>
        <div className="ml-auto flex items-center gap-1 md:ml-2">
          <CartIndicator />
          <AccountMenu />
        </div>
      </div>
      <div className="border-t px-4 py-2 md:hidden">
        <SearchBar />
      </div>
    </header>
  );
}

function CartIndicator() {
  const { cart } = useCart();
  const count = cart?.itemCount ?? 0;
  return (
    <Link href="/cart" aria-label={`Shopping cart, ${count} item(s)`}
          className={cn(buttonVariants({ variant: "ghost", size: "icon" }), "relative")}>
      <ShoppingCart className="size-5" />
      {count > 0 && (
        <span className="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-primary text-[10px] font-semibold text-primary-foreground">
          {count > 99 ? "99+" : count}
        </span>
      )}
    </Link>
  );
}

function AccountMenu() {
  const { user, logout, isStaff } = useAuth();
  const router = useRouter();

  if (!user) {
    return (
      <div className="flex items-center gap-1">
        <Link href="/login" className={buttonVariants({ variant: "ghost", size: "sm" })}>Sign in</Link>
        <Link href="/register" className={cn(buttonVariants({ size: "sm" }), "hidden sm:inline-flex")}>Create account</Link>
      </div>
    );
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className={buttonVariants({ variant: "ghost", size: "icon" })} aria-label="Account menu">
        <User className="size-5" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuGroup>
          <DropdownMenuLabel>
            <div className="truncate font-medium">{user.firstName} {user.lastName}</div>
            <div className="truncate text-xs font-normal text-muted-foreground">{user.email}</div>
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => router.push("/account")}><User /> My account</DropdownMenuItem>
        <DropdownMenuItem onClick={() => router.push("/account/orders")}><Package /> Orders</DropdownMenuItem>
        {isStaff && <DropdownMenuItem onClick={() => router.push("/admin")}><LayoutDashboard /> Admin dashboard</DropdownMenuItem>}
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={async () => {
          await logout();
          router.push("/");
          router.refresh();
        }}><LogOut /> Sign out</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function MobileNav({ categories }: { categories: Category[] }) {
  // Controlled so the sheet closes when a link is followed (client navigation keeps it mounted).
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger className={cn(buttonVariants({ variant: "ghost", size: "icon" }), "lg:hidden")} aria-label="Open menu">
        <Menu className="size-5" />
      </SheetTrigger>
      <SheetContent side="left">
        <SheetHeader>
          <SheetTitle>{SITE_NAME}</SheetTitle>
        </SheetHeader>
        <nav aria-label="Mobile" className="flex flex-col gap-1 px-4">
          <Link href="/products" onClick={close} className="rounded-md px-2 py-2 hover:bg-muted">All products</Link>
          {categories.map((c) => (
            <Link key={c.id} href={`/categories/${c.id}`} onClick={close} className="rounded-md px-2 py-2 hover:bg-muted">{c.name}</Link>
          ))}
          <Link href="/cart" onClick={close} className="rounded-md px-2 py-2 hover:bg-muted">Cart</Link>
          <Link href="/account" onClick={close} className="rounded-md px-2 py-2 hover:bg-muted">My account</Link>
        </nav>
      </SheetContent>
    </Sheet>
  );
}
