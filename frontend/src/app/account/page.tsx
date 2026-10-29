import type { Metadata } from "next";
import Link from "next/link";

import { MyOrdersList } from "@/components/account/order-views";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireUser } from "@/lib/auth/session";

export const metadata: Metadata = { title: "My account" };

export default async function AccountPage({ searchParams }: PageProps<"/account">) {
  const user = await requireUser("/account");
  const { welcome } = await searchParams;
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Hello, {user.firstName}</h1>
      {welcome && (
        <Alert><AlertDescription>Welcome! We sent a verification link to {user.email}.</AlertDescription></Alert>
      )}
      <div className="grid gap-4 sm:grid-cols-3">
        <Card><CardHeader><CardTitle className="text-base">Email</CardTitle></CardHeader>
          <CardContent className="text-sm">{user.email}<br /><span className="text-muted-foreground">{user.emailVerified ? "Verified" : "Not verified"}</span></CardContent></Card>
        <Card><CardHeader><CardTitle className="text-base">Security</CardTitle></CardHeader>
          <CardContent className="text-sm"><Link href="/account/sessions" className="underline">Review active sessions</Link></CardContent></Card>
        <Card><CardHeader><CardTitle className="text-base">Addresses</CardTitle></CardHeader>
          <CardContent className="text-sm"><Link href="/account/profile" className="underline">Manage addresses</Link></CardContent></Card>
      </div>
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Recent orders</h2>
        <MyOrdersList />
      </section>
    </div>
  );
}
