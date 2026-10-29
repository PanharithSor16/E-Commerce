import type { Metadata } from "next";

import { SessionList } from "@/components/account/account-settings";

export const metadata: Metadata = { title: "Active sessions" };

export default function SessionsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Active sessions</h1>
        <p className="text-sm text-muted-foreground">Devices currently signed in to your account. Sign out any you do not recognize.</p>
      </div>
      <SessionList />
    </div>
  );
}
