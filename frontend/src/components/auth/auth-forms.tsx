"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

import { Checkbox, Field } from "@/components/common/form";
import { useAuth } from "@/components/providers/auth-provider";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ApiError, errorMessage } from "@/lib/api/client";
import { authApi } from "@/lib/api/endpoints";
import { getConsent, saveConsent } from "@/lib/analytics/consent";
import {
  forgotPasswordSchema, loginSchema, registerSchema, resetPasswordSchema,
  type LoginValues, type RegisterValues,
} from "@/lib/validation/schemas";

/** Only same-site relative paths are accepted as post-login destinations (no open redirects). */
export function safeNext(next: string | null, fallback = "/account"): string {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : fallback;
}

/** Maps server-side field errors onto the form. */
function applyServerErrors<T extends FieldValues>(e: unknown, setError: UseFormSetError<T>, fields: string[]) {
  if (e instanceof ApiError) {
    for (const [field, message] of Object.entries(e.fieldErrors)) {
      if (fields.includes(field)) {
        setError(field as Path<T>, { message });
      }
    }
  }
}

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const { setUser } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Sign in</CardTitle>
        <CardDescription>Welcome back. Enter your email and password.</CardDescription>
      </CardHeader>
      <form noValidate onSubmit={handleSubmit(async (values) => {
        setError(null);
        try {
          const user = await authApi.login(values);
          setUser(user);
          const staff = user.roles.includes("ROLE_ADMIN") || user.roles.includes("ROLE_STAFF");
          router.push(safeNext(params.get("next"), staff ? "/admin" : "/account"));
          router.refresh();
        } catch (e) {
          setError(errorMessage(e));
        }
      })}>
        <CardContent className="space-y-4">
          {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
          <Field id="email" label="Email" error={errors.email?.message}>
            <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...register("email")} />
          </Field>
          <Field id="password" label="Password" error={errors.password?.message}>
            <Input id="password" type="password" autoComplete="current-password" aria-invalid={!!errors.password} {...register("password")} />
          </Field>
          <div className="text-right text-sm">
            <Link href="/forgot-password" className="text-muted-foreground hover:text-foreground">Forgot your password?</Link>
          </div>
        </CardContent>
        <CardFooter className="flex flex-col gap-3">
          <Button type="submit" className="w-full" disabled={isSubmitting}>{isSubmitting ? "Signing in…" : "Sign in"}</Button>
          <p className="text-sm text-muted-foreground">
            New here? <Link href={`/register${params.get("next") ? `?next=${encodeURIComponent(params.get("next")!)}` : ""}`} className="text-foreground underline">Create an account</Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}

export function RegisterForm() {
  const router = useRouter();
  const params = useSearchParams();
  const { setUser } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const registerForm = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      firstName: "", lastName: "", email: "", password: "", confirmPassword: "", acceptTerms: false,
      analyticsConsent: false, marketingConsent: false,
    },
  });
  const { register, handleSubmit, setValue, setError: setFieldError, formState: { errors, isSubmitting } } = registerForm;
  // Pre-tick analytics if the visitor already accepted the banner (read after hydration).
  useEffect(() => {
    setValue("analyticsConsent", getConsent()?.analytics ?? false);
  }, [setValue]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Create your account</CardTitle>
        <CardDescription>Track orders, save addresses and check out faster.</CardDescription>
      </CardHeader>
      <form noValidate onSubmit={handleSubmit(async (values) => {
        setError(null);
        try {
          const user = await authApi.register({
            email: values.email, password: values.password, firstName: values.firstName, lastName: values.lastName,
            analyticsConsent: values.analyticsConsent, marketingConsent: values.marketingConsent,
          });
          saveConsent(values.analyticsConsent, values.marketingConsent);
          setUser(user);
          router.push(safeNext(params.get("next"), "/account?welcome=1"));
          router.refresh();
        } catch (e) {
          applyServerErrors(e, setFieldError, ["email", "password", "firstName", "lastName"]);
          setError(errorMessage(e));
        }
      })}>
        <CardContent className="space-y-4">
          {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="firstName" label="First name" error={errors.firstName?.message}>
              <Input id="firstName" autoComplete="given-name" aria-invalid={!!errors.firstName} {...register("firstName")} />
            </Field>
            <Field id="lastName" label="Last name" error={errors.lastName?.message}>
              <Input id="lastName" autoComplete="family-name" aria-invalid={!!errors.lastName} {...register("lastName")} />
            </Field>
          </div>
          <Field id="email" label="Email" error={errors.email?.message}>
            <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...register("email")} />
          </Field>
          <Field id="password" label="Password" hint="At least 10 characters. A passphrase works well." error={errors.password?.message}>
            <Input id="password" type="password" autoComplete="new-password" aria-invalid={!!errors.password} {...register("password")} />
          </Field>
          <Field id="confirmPassword" label="Confirm password" error={errors.confirmPassword?.message}>
            <Input id="confirmPassword" type="password" autoComplete="new-password" aria-invalid={!!errors.confirmPassword} {...register("confirmPassword")} />
          </Field>
          <div className="space-y-2">
            <Checkbox label={<>I accept the terms and the <Link href="/privacy" className="underline">privacy policy</Link></>} {...register("acceptTerms")} />
            {errors.acceptTerms && <p role="alert" className="text-xs text-destructive">{errors.acceptTerms.message}</p>}
            <Checkbox label="Allow privacy-friendly analytics to help improve the store (optional)" {...register("analyticsConsent")} />
            <Checkbox label="Send me offers by email (optional)" {...register("marketingConsent")} />
          </div>
        </CardContent>
        <CardFooter className="flex flex-col gap-3">
          <Button type="submit" className="w-full" disabled={isSubmitting}>{isSubmitting ? "Creating account…" : "Create account"}</Button>
          <p className="text-sm text-muted-foreground">Already have an account? <Link href="/login" className="text-foreground underline">Sign in</Link></p>
        </CardFooter>
      </form>
    </Card>
  );
}

export function ForgotPasswordForm() {
  const [sent, setSent] = useState(false);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<{ email: string }>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });
  return (
    <Card>
      <CardHeader>
        <CardTitle>Reset your password</CardTitle>
        <CardDescription>We will email you a link to choose a new password.</CardDescription>
      </CardHeader>
      {sent ? (
        <CardContent>
          <Alert><AlertDescription>If an account exists for that email, a reset link is on its way. The link is valid for 30 minutes.</AlertDescription></Alert>
        </CardContent>
      ) : (
        <form noValidate onSubmit={handleSubmit(async ({ email }) => {
          try {
            await authApi.forgotPassword(email);
          } finally {
            setSent(true); // same response whether or not the account exists
          }
        })}>
          <CardContent>
            <Field id="email" label="Email" error={errors.email?.message}>
              <Input id="email" type="email" autoComplete="email" {...register("email")} />
            </Field>
          </CardContent>
          <CardFooter className="mt-4 flex flex-col gap-3">
            <Button type="submit" className="w-full" disabled={isSubmitting}>Send reset link</Button>
            <Link href="/login" className="text-sm text-muted-foreground hover:text-foreground">Back to sign in</Link>
          </CardFooter>
        </form>
      )}
    </Card>
  );
}

export function ResetPasswordForm() {
  const params = useSearchParams();
  const router = useRouter();
  const token = params.get("token");
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<{ newPassword: string; confirmPassword: string }>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });
  if (!token) {
    return <Alert variant="destructive"><AlertDescription>This reset link is invalid. Request a new one.</AlertDescription></Alert>;
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>Choose a new password</CardTitle>
        <CardDescription>For your security, you will be signed out on all devices.</CardDescription>
      </CardHeader>
      <form noValidate onSubmit={handleSubmit(async ({ newPassword }) => {
        setError(null);
        try {
          await authApi.resetPassword(token, newPassword);
          router.push("/login?reset=1");
        } catch (e) {
          setError(errorMessage(e));
        }
      })}>
        <CardContent className="space-y-4">
          {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
          <Field id="newPassword" label="New password" error={errors.newPassword?.message}>
            <Input id="newPassword" type="password" autoComplete="new-password" {...register("newPassword")} />
          </Field>
          <Field id="confirmPassword" label="Confirm new password" error={errors.confirmPassword?.message}>
            <Input id="confirmPassword" type="password" autoComplete="new-password" {...register("confirmPassword")} />
          </Field>
        </CardContent>
        <CardFooter className="mt-4">
          <Button type="submit" className="w-full" disabled={isSubmitting}>Set new password</Button>
        </CardFooter>
      </form>
    </Card>
  );
}

export function VerifyEmail() {
  const params = useSearchParams();
  const token = params.get("token");
  const { refresh } = useAuth();
  const [state, setState] = useState<"working" | "ok" | "error">(token ? "working" : "error");
  const started = useRef(false);

  useEffect(() => {
    if (!token || started.current) {
      return;
    }
    started.current = true; // single-use token: never submit twice (Strict Mode)
    authApi.verifyEmail(token)
      .then(() => { setState("ok"); void refresh(); })
      .catch(() => setState("error"));
  }, [token, refresh]);

  return (
    <Card>
      <CardHeader><CardTitle>Email verification</CardTitle></CardHeader>
      <CardContent>
        {state === "working" && <p>Verifying…</p>}
        {state === "ok" && <Alert><AlertDescription>Your email address is verified. Thank you!</AlertDescription></Alert>}
        {state === "error" && <Alert variant="destructive"><AlertDescription>This verification link is invalid or has expired. You can request a new one from your account.</AlertDescription></Alert>}
      </CardContent>
      <CardFooter><Link href="/account" className="text-sm underline">Go to my account</Link></CardFooter>
    </Card>
  );
}
