"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Monitor, Smartphone, Tablet, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { ConfirmationDialog } from "@/components/common/confirmation-dialog";
import { Checkbox, Field } from "@/components/common/form";
import { EmptyState, ErrorState, LoadingState } from "@/components/common/states";
import { useAuth } from "@/components/providers/auth-provider";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api/client";
import { accountApi, authApi } from "@/lib/api/endpoints";
import type { Address, SessionInfo } from "@/lib/api/types";
import { saveConsent } from "@/lib/analytics/consent";
import { formatDateTime } from "@/lib/format";
import { addressSchema, changePasswordSchema, profileSchema, type AddressValues } from "@/lib/validation/schemas";

export function ProfileForm() {
  const { user, setUser } = useAuth();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(profileSchema),
    defaultValues: { firstName: user?.firstName ?? "", lastName: user?.lastName ?? "", phoneNumber: user?.phoneNumber ?? "" },
  });
  return (
    <Card>
      <CardHeader><CardTitle>Personal details</CardTitle><CardDescription>{user?.email}</CardDescription></CardHeader>
      <form noValidate onSubmit={handleSubmit(async (v) => {
        try {
          setUser(await accountApi.updateProfile({ ...v, phoneNumber: v.phoneNumber || undefined }));
          toast.success("Profile saved");
        } catch (e) {
          toast.error(errorMessage(e));
        }
      })}>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field id="firstName" label="First name" error={errors.firstName?.message}><Input id="firstName" {...register("firstName")} /></Field>
          <Field id="lastName" label="Last name" error={errors.lastName?.message}><Input id="lastName" {...register("lastName")} /></Field>
          <Field id="phone" label="Phone" error={errors.phoneNumber?.message} className="sm:col-span-2"><Input id="phone" type="tel" {...register("phoneNumber")} /></Field>
        </CardContent>
        <CardFooter className="mt-4"><Button type="submit" disabled={isSubmitting}>Save</Button></CardFooter>
      </form>
    </Card>
  );
}

export function AddressBook() {
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [adding, setAdding] = useState(false);
  const load = useCallback(() => {
    accountApi.addresses().then(setAddresses).catch(() => setAddresses([]));
  }, []);
  useEffect(load, [load]);
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<AddressValues>({
    resolver: zodResolver(addressSchema),
    defaultValues: { recipientName: "", phoneNumber: "", line1: "", line2: "", city: "", region: "", postalCode: "", countryCode: "" },
  });

  return (
    <Card>
      <CardHeader><CardTitle>Address book</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        {!addresses ? <LoadingState /> : addresses.length === 0 && !adding ? (
          <EmptyState title="No saved addresses" description="Addresses you save at checkout appear here." />
        ) : (
          <ul className="space-y-2">
            {addresses.map((a) => (
              <li key={a.id} className="flex items-start justify-between gap-3 rounded-lg border p-3 text-sm">
                <div>
                  <p className="font-medium">{a.recipientName} {a.defaultShipping && <Badge variant="secondary">Default</Badge>}</p>
                  <p className="text-muted-foreground">{a.line1}{a.line2 ? `, ${a.line2}` : ""}, {a.city} {a.postalCode}, {a.countryCode}</p>
                </div>
                <Button variant="ghost" size="icon-sm" aria-label="Delete address" onClick={async () => {
                  await accountApi.deleteAddress(a.id);
                  load();
                }}><Trash2 /></Button>
              </li>
            ))}
          </ul>
        )}
        {adding ? (
          <form noValidate className="grid gap-3 sm:grid-cols-2" onSubmit={handleSubmit(async (v) => {
            try {
              await accountApi.addAddress({
                address: { ...v, countryCode: v.countryCode.toUpperCase(), phoneNumber: v.phoneNumber || undefined, line2: v.line2 || undefined, region: v.region || undefined, postalCode: v.postalCode || undefined },
                defaultShipping: (addresses?.length ?? 0) === 0, defaultBilling: (addresses?.length ?? 0) === 0,
              });
              reset();
              setAdding(false);
              load();
            } catch (e) {
              toast.error(errorMessage(e));
            }
          })}>
            <Field id="a-name" label="Full name" error={errors.recipientName?.message} className="sm:col-span-2"><Input id="a-name" {...register("recipientName")} /></Field>
            <Field id="a-line1" label="Address" error={errors.line1?.message} className="sm:col-span-2"><Input id="a-line1" {...register("line1")} /></Field>
            <Field id="a-city" label="City" error={errors.city?.message}><Input id="a-city" {...register("city")} /></Field>
            <Field id="a-postal" label="Postal code" error={errors.postalCode?.message}><Input id="a-postal" {...register("postalCode")} /></Field>
            <Field id="a-country" label="Country code" error={errors.countryCode?.message}><Input id="a-country" maxLength={2} {...register("countryCode")} /></Field>
            <Field id="a-phone" label="Phone" error={errors.phoneNumber?.message}><Input id="a-phone" type="tel" {...register("phoneNumber")} /></Field>
            <div className="flex gap-2 sm:col-span-2">
              <Button type="submit" disabled={isSubmitting}>Save address</Button>
              <Button type="button" variant="outline" onClick={() => setAdding(false)}>Cancel</Button>
            </div>
          </form>
        ) : (
          <Button variant="outline" onClick={() => setAdding(true)}>Add address</Button>
        )}
      </CardContent>
    </Card>
  );
}

export function ConsentSettings() {
  const [state, setState] = useState<{ analytics: boolean; marketing: boolean } | null>(null);
  useEffect(() => {
    accountApi.consent().then(setState).catch(() => setState({ analytics: false, marketing: false }));
  }, []);
  if (!state) {
    return null;
  }
  const update = async (next: { analytics: boolean; marketing: boolean }) => {
    setState(next);
    await accountApi.updateConsent(next);
    saveConsent(next.analytics, next.marketing);
    toast.success("Privacy preferences saved");
  };
  return (
    <Card>
      <CardHeader><CardTitle>Privacy preferences</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        <Checkbox label="Allow privacy-friendly analytics" checked={state.analytics} onChange={(e) => update({ ...state, analytics: e.target.checked })} />
        <Checkbox label="Receive marketing emails" checked={state.marketing} onChange={(e) => update({ ...state, marketing: e.target.checked })} />
      </CardContent>
    </Card>
  );
}

export function SecuritySettings() {
  const { user } = useAuth();
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });
  return (
    <div className="space-y-6">
      {!user?.emailVerified && (
        <Alert>
          <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
            Your email address is not verified.
            <Button size="sm" variant="outline" onClick={async () => {
              await authApi.resendVerification();
              toast.success("Verification email sent");
            }}>Resend verification email</Button>
          </AlertDescription>
        </Alert>
      )}
      <Card>
        <CardHeader>
          <CardTitle>Change password</CardTitle>
          <CardDescription>Changing your password signs you out on all other devices.</CardDescription>
        </CardHeader>
        <form noValidate onSubmit={handleSubmit(async (v) => {
          try {
            await accountApi.changePassword(v.currentPassword, v.newPassword);
            reset();
            toast.success("Password changed. Other sessions were signed out.");
          } catch (e) {
            toast.error(errorMessage(e));
          }
        })}>
          <CardContent className="space-y-4">
            <Field id="cur" label="Current password" error={errors.currentPassword?.message}><Input id="cur" type="password" autoComplete="current-password" {...register("currentPassword")} /></Field>
            <Field id="new" label="New password" error={errors.newPassword?.message}><Input id="new" type="password" autoComplete="new-password" {...register("newPassword")} /></Field>
            <Field id="conf" label="Confirm new password" error={errors.confirmPassword?.message}><Input id="conf" type="password" autoComplete="new-password" {...register("confirmPassword")} /></Field>
          </CardContent>
          <CardFooter className="mt-4"><Button type="submit" disabled={isSubmitting}>Change password</Button></CardFooter>
        </form>
      </Card>
    </div>
  );
}

export function DeviceIcon({ device }: { device: string | null }) {
  if (device === "MOBILE") {
    return <Smartphone className="size-5" aria-label="Mobile" />;
  }
  if (device === "TABLET") {
    return <Tablet className="size-5" aria-label="Tablet" />;
  }
  return <Monitor className="size-5" aria-label="Desktop" />;
}

export function SessionList() {
  const [sessions, setSessions] = useState<SessionInfo[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmAll, setConfirmAll] = useState(false);
  const load = useCallback(() => {
    accountApi.sessions().then(setSessions).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  if (error) {
    return <ErrorState message={error} onRetry={load} />;
  }
  if (!sessions) {
    return <LoadingState />;
  }
  return (
    <div className="space-y-4">
      <ul className="divide-y rounded-xl border">
        {sessions.map((s) => (
          <li key={s.id} className="flex items-center gap-4 p-4">
            <DeviceIcon device={s.deviceCategory} />
            <div className="min-w-0 flex-1 text-sm">
              <p className="font-medium">{s.browser ?? "Unknown browser"} on {s.operatingSystem ?? "unknown OS"} {s.current && <Badge>This device</Badge>}</p>
              <p className="text-muted-foreground">Signed in {formatDateTime(s.createdAt)} · last active {formatDateTime(s.lastSeenAt)}{s.ipAddress ? ` · ${s.ipAddress}` : ""}</p>
            </div>
            {!s.current && (
              <Button variant="outline" size="sm" onClick={async () => {
                await accountApi.revokeSession(s.id);
                toast.success("Session signed out");
                load();
              }}>Sign out</Button>
            )}
          </li>
        ))}
      </ul>
      {sessions.length > 1 && <Button variant="outline" onClick={() => setConfirmAll(true)}>Sign out all other devices</Button>}
      <ConfirmationDialog open={confirmAll} onOpenChange={setConfirmAll} title="Sign out all other devices?" confirmLabel="Sign out others"
        onConfirm={async () => {
          await accountApi.revokeOtherSessions();
          load();
        }} />
    </div>
  );
}
