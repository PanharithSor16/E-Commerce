import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";

import { ConsentBanner } from "@/components/analytics/consent-banner";
import { PageViewTracker } from "@/components/analytics/page-view-tracker";
import { AppProviders } from "@/components/providers/app-providers";
import { getCurrentUser } from "@/lib/auth/session";

import "./globals.css";

const geistSans = Geist({ variable: "--font-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });

const SITE_NAME = process.env.NEXT_PUBLIC_SITE_NAME ?? "Shop";

export const metadata: Metadata = {
  title: { default: SITE_NAME, template: `%s | ${SITE_NAME}` },
  description: "Quality products, delivered fast.",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1 };

export default async function RootLayout({ children }: LayoutProps<"/">) {
  // Asks the backend who is signed in; seeds the client auth context for header/menus.
  const user = await getCurrentUser().catch(() => null);
  return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col bg-background font-sans text-foreground">
        <AppProviders user={user}>
          {children}
          <PageViewTracker />
          <ConsentBanner />
        </AppProviders>
      </body>
    </html>
  );
}
