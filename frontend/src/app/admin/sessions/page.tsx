import type { Metadata } from "next";

import { SessionsView } from "@/components/admin/security";

export const metadata: Metadata = { title: "Sessions" };

export default function AdminSessionsPage() {
  return <SessionsView />;
}
