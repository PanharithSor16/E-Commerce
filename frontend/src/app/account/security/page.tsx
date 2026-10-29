import type { Metadata } from "next";

import { SecuritySettings } from "@/components/account/account-settings";

export const metadata: Metadata = { title: "Security" };

export default function SecurityPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Security</h1>
      <SecuritySettings />
    </div>
  );
}
