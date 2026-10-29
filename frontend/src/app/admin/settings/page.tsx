import type { Metadata } from "next";

import { SettingsView } from "@/components/admin/catalog-settings";

export const metadata: Metadata = { title: "Settings" };

export default function AdminSettingsPage() {
  return <SettingsView />;
}
