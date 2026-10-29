import type { Metadata } from "next";

import { AdminHeader, AdminSidebar } from "@/components/admin/admin-shell";
import { requireStaff } from "@/lib/auth/session";

export const metadata: Metadata = { title: { default: "Admin", template: "%s | Admin" }, robots: { index: false } };

/** Server-side gate for staff/admins. Each admin API call is authorized again by the backend. */
export default async function AdminLayout({ children }: LayoutProps<"/admin">) {
  const user = await requireStaff();
  return (
    <div className="flex min-h-screen flex-1">
      <AdminSidebar permissions={user.permissions} />
      <div className="flex min-w-0 flex-1 flex-col">
        <AdminHeader permissions={user.permissions} />
        <main className="flex-1 space-y-6 p-4 md:p-6">{children}</main>
      </div>
    </div>
  );
}
