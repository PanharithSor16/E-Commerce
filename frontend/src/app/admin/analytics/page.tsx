import type { Metadata } from "next";

import { AnalyticsReports } from "@/components/admin/analytics-reports";

export const metadata: Metadata = { title: "Analytics" };

export default function AdminAnalyticsPage() {
  return <AnalyticsReports />;
}
