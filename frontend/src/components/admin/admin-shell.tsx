"use client";

import {
  BarChart3, FolderTree, KeyRound, LayoutDashboard, LogOut, Menu, MonitorSmartphone, Package, Settings, ShoppingBag,
  ShoppingCart, Users,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";

import { useAuth } from "@/components/providers/auth-provider";
import { Button, buttonVariants } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import type { Permission } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const NAV: { href: string; label: string; icon: React.ComponentType<{ className?: string }>; permission?: Permission }[] = [
  { href: "/admin", label: "Dashboard", icon: LayoutDashboard, permission: "ANALYTICS_READ" },
  { href: "/admin/products", label: "Products", icon: Package, permission: "CATALOG_READ" },
  { href: "/admin/categories", label: "Categories", icon: FolderTree, permission: "CATALOG_READ" },
  { href: "/admin/orders", label: "Orders", icon: ShoppingCart, permission: "ORDER_READ" },
  { href: "/admin/customers", label: "Customers", icon: Users, permission: "CUSTOMER_READ" },
  { href: "/admin/analytics", label: "Analytics", icon: BarChart3, permission: "ANALYTICS_READ" },
  { href: "/admin/login-history", label: "Login history", icon: KeyRound, permission: "SECURITY_LOG_READ" },
  { href: "/admin/sessions", label: "Sessions", icon: MonitorSmartphone, permission: "SECURITY_LOG_READ" },
  { href: "/admin/settings", label: "Settings", icon: Settings, permission: "SETTINGS_WRITE" },
];

function NavLinks({ permissions, onNavigate }: { permissions: Permission[]; onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav aria-label="Admin" className="flex flex-col gap-1">
      {NAV.filter((n) => !n.permission || permissions.includes(n.permission)).map((n) => {
        const active = n.href === "/admin" ? pathname === "/admin" : pathname.startsWith(n.href);
        const Icon = n.icon;
        return (
          <Link key={n.href} href={n.href} onClick={onNavigate} aria-current={active ? "page" : undefined}
                className={cn("flex items-center gap-2 rounded-md px-3 py-2 text-sm",
                  active ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground" : "text-muted-foreground hover:bg-sidebar-accent/60")}>
            <Icon className="size-4" /> {n.label}
          </Link>
        );
      })}
    </nav>
  );
}

export function AdminSidebar({ permissions }: { permissions: Permission[] }) {
  return (
    <aside className="hidden w-60 shrink-0 border-r bg-sidebar p-4 md:block">
      <Link href="/admin" className="mb-6 flex items-center gap-2 px-3 font-semibold">
        <ShoppingBag className="size-5" aria-hidden /> Admin
      </Link>
      <NavLinks permissions={permissions} />
    </aside>
  );
}

export function AdminHeader({ permissions }: { permissions: Permission[] }) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b bg-background/95 px-4 backdrop-blur">
      <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
        <SheetTrigger className={cn(buttonVariants({ variant: "ghost", size: "icon" }), "md:hidden")} aria-label="Open admin menu">
          <Menu className="size-5" />
        </SheetTrigger>
        <SheetContent side="left">
          <SheetHeader><SheetTitle>Admin</SheetTitle></SheetHeader>
          <div className="px-4"><NavLinks permissions={permissions} onNavigate={() => setMenuOpen(false)} /></div>
        </SheetContent>
      </Sheet>
      <Link href="/" className="text-sm text-muted-foreground hover:text-foreground">View store</Link>
      <div className="ml-auto flex items-center gap-3 text-sm">
        <span className="hidden text-muted-foreground sm:inline">{user?.email} · {user?.roles.includes("ROLE_ADMIN") ? "Admin" : "Staff"}</span>
        <Button variant="ghost" size="sm" onClick={async () => {
          await logout();
          router.push("/login");
        }}><LogOut /> Sign out</Button>
      </div>
    </header>
  );
}

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold">{title}</h1>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
