import type { Metadata } from "next";
import { Suspense } from "react";

import { LoginHistoryView } from "@/components/admin/security";

export const metadata: Metadata = { title: "Login history" };

export default function LoginHistoryPage() {
  return (
    <Suspense>
      <LoginHistoryView />
    </Suspense>
  );
}
