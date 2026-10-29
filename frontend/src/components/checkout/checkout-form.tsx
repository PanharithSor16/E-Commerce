"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import type { UseFormRegister, FieldErrors } from "react-hook-form";

import { CartSummary } from "@/components/cart/shopping-cart";
import { Checkbox, Field } from "@/components/common/form";
import { EmptyState, ErrorState, LoadingState } from "@/components/common/states";
import { useAuth } from "@/components/providers/auth-provider";
import { useCart } from "@/components/providers/cart-provider";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, errorMessage, newIdempotencyKey } from "@/lib/api/client";
import { accountApi, catalogApi, orderApi } from "@/lib/api/endpoints";
import type { Address, AddressInput, Quote, ShippingMethod } from "@/lib/api/types";
import { track } from "@/lib/analytics/track";
import { formatMoney } from "@/lib/format";
import { checkoutSchema, type AddressValues, type CheckoutValues } from "@/lib/validation/schemas";

const emptyAddress = { recipientName: "", phoneNumber: "", line1: "", line2: "", city: "", region: "", postalCode: "", countryCode: "" };

export function CheckoutForm() {
  const router = useRouter();
  const { user } = useAuth();
  const { cart, loading: cartLoading, refresh } = useCart();
  const [methods, setMethods] = useState<ShippingMethod[]>([]);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [savedAddressId, setSavedAddressId] = useState<number | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  // One key per checkout attempt: a double-click or network retry cannot create two orders.
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);

  const form = useForm<CheckoutValues>({
    resolver: zodResolver(checkoutSchema),
    defaultValues: {
      shipping: { ...emptyAddress, recipientName: user ? `${user.firstName} ${user.lastName}` : "" },
      billingSameAsShipping: true,
      billing: emptyAddress,
      shippingMethodCode: "",
      couponCode: "",
      note: "",
      saveAddress: true,
    },
  });
  const { register, handleSubmit, setValue, control, formState: { errors, isSubmitting } } = form;
  const shippingMethodCode = useWatch({ control, name: "shippingMethodCode" });
  const billingSame = useWatch({ control, name: "billingSameAsShipping" });

  useEffect(() => {
    Promise.all([catalogApi.shippingMethods(), accountApi.addresses()])
      .then(([m, a]) => {
        setMethods(m);
        setAddresses(a);
        if (m[0]) {
          setValue("shippingMethodCode", m[0].code);
        }
        const def = a.find((x) => x.defaultShipping) ?? a[0];
        if (def) {
          setSavedAddressId(def.id);
        }
      })
      .catch((e) => setLoadError(errorMessage(e)));
  }, [setValue]);

  useEffect(() => {
    if (cart && cart.items.length > 0) {
      track("BEGIN_CHECKOUT", { itemCount: cart.itemCount });
    }
    // only once per visit to the checkout page
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cart?.id]);

  // Totals always come from the server for the selected method and the cart's coupon.
  useEffect(() => {
    if (!shippingMethodCode || !cart || cart.items.length === 0) {
      return;
    }
    orderApi.quote(shippingMethodCode, cart.couponCode ?? undefined)
      .then(setQuote)
      .catch((e) => setSubmitError(errorMessage(e)));
  }, [shippingMethodCode, cart]);

  if (loadError) {
    return <ErrorState message={loadError} />;
  }
  if (cartLoading && !cart) {
    return <LoadingState />;
  }
  if (!cart || cart.items.length === 0) {
    return <EmptyState title="Your cart is empty" action={<Link href="/products" className={buttonVariants()}>Browse products</Link>} />;
  }

  const useSaved = savedAddressId !== null;

  async function onSubmit(values: CheckoutValues) {
    setSubmitError(null);
    const toInput = (a: AddressValues): AddressInput => ({
      recipientName: a.recipientName, phoneNumber: a.phoneNumber || undefined, line1: a.line1, line2: a.line2 || undefined,
      city: a.city, region: a.region || undefined, postalCode: a.postalCode || undefined, countryCode: a.countryCode.toUpperCase(),
    });
    try {
      const order = await orderApi.place({
        shippingAddressId: useSaved ? savedAddressId! : undefined,
        shippingAddress: useSaved ? undefined : toInput(values.shipping),
        billingSameAsShipping: values.billingSameAsShipping,
        billingAddress: values.billingSameAsShipping || !values.billing ? undefined : toInput(values.billing as AddressValues),
        shippingMethodCode: values.shippingMethodCode,
        couponCode: cart?.couponCode ?? undefined,
        note: values.note || undefined,
        saveAddress: !useSaved && values.saveAddress,
      }, idempotencyKey);
      setIdempotencyKey(newIdempotencyKey());
      await refresh();
      router.push(`/checkout/payment/${order.id}`);
    } catch (e) {
      if (e instanceof ApiError && e.code === "CART_CHANGED") {
        await refresh();
      }
      setSubmitError(errorMessage(e));
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit, () => setSubmitError(null))} noValidate className="grid gap-8 lg:grid-cols-[1fr_360px]">
      <div className="space-y-6">
        {!user?.emailVerified && (
          <Alert><AlertDescription>Your email address is not verified yet. Check your inbox for the verification link.</AlertDescription></Alert>
        )}
        <Card>
          <CardHeader><CardTitle>Shipping address</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            {addresses.length > 0 && (
              <fieldset className="space-y-2">
                <legend className="sr-only">Saved addresses</legend>
                {addresses.map((a) => (
                  <label key={a.id} className="flex items-start gap-2 rounded-lg border p-3 text-sm">
                    <input type="radio" name="savedAddress" className="mt-1" checked={savedAddressId === a.id}
                           onChange={() => setSavedAddressId(a.id)} />
                    <span>{a.recipientName}, {a.line1}{a.line2 ? `, ${a.line2}` : ""}, {a.city} {a.postalCode} {a.countryCode}</span>
                  </label>
                ))}
                <label className="flex items-center gap-2 rounded-lg border p-3 text-sm">
                  <input type="radio" name="savedAddress" checked={savedAddressId === null} onChange={() => setSavedAddressId(null)} />
                  Use a new address
                </label>
              </fieldset>
            )}
            {!useSaved && (
              <>
                <AddressFields prefix="shipping" register={register} errors={errors.shipping} />
                <Checkbox label="Save this address to my account" {...register("saveAddress")} />
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Shipping method</CardTitle></CardHeader>
          <CardContent className="space-y-2">
            {methods.map((m) => (
              <label key={m.code} className="flex items-center justify-between gap-2 rounded-lg border p-3 text-sm">
                <span className="flex items-center gap-2">
                  <input type="radio" value={m.code} {...register("shippingMethodCode")} />
                  <span>
                    <span className="font-medium">{m.name}</span>
                    {m.estimatedDaysMin && <span className="text-muted-foreground"> · {m.estimatedDaysMin}–{m.estimatedDaysMax} business days</span>}
                  </span>
                </span>
                <span>{formatMoney(m.fee, cart.currency)}{m.freeShippingThreshold && <span className="text-xs text-muted-foreground"> (free over {formatMoney(m.freeShippingThreshold, cart.currency)})</span>}</span>
              </label>
            ))}
            {errors.shippingMethodCode && <p className="text-xs text-destructive">{errors.shippingMethodCode.message}</p>}
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Billing</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <Checkbox label="Billing address is the same as shipping" {...register("billingSameAsShipping")} />
            {!billingSame && <AddressFields prefix="billing" register={register} errors={errors.billing} />}
          </CardContent>
        </Card>

        <Field id="note" label="Order note (optional)" error={errors.note?.message}>
          <Textarea id="note" rows={3} maxLength={500} {...register("note")} />
        </Field>
      </div>

      <aside className="space-y-4">
        {quote ? (
          <CartSummary shippingLabel="Shipping" cart={{
            subtotal: quote.subtotal, discountTotal: quote.discountTotal, shippingEstimate: quote.shippingTotal,
            taxTotal: quote.taxTotal, total: quote.grandTotal, currency: quote.currency, couponCode: quote.couponCode,
          }} />
        ) : <LoadingState label="Calculating totals…" />}
        {submitError && <Alert variant="destructive"><AlertDescription>{submitError}</AlertDescription></Alert>}
        <Button type="submit" size="lg" className="w-full" disabled={isSubmitting || !quote}>
          {isSubmitting ? "Placing order…" : "Place order and pay"}
        </Button>
        <p className="text-center text-xs text-muted-foreground">Prices and totals are confirmed by our server when you place the order.</p>
      </aside>
    </form>
  );
}

function AddressFields({ prefix, register, errors }: {
  prefix: "shipping" | "billing";
  register: UseFormRegister<CheckoutValues>;
  errors?: FieldErrors<AddressValues>;
}) {
  const id = (f: string) => `${prefix}-${f}`;
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field id={id("name")} label="Full name" error={errors?.recipientName?.message} className="sm:col-span-2">
        <Input id={id("name")} autoComplete="name" aria-invalid={!!errors?.recipientName} {...register(`${prefix}.recipientName`)} />
      </Field>
      <Field id={id("line1")} label="Address" error={errors?.line1?.message} className="sm:col-span-2">
        <Input id={id("line1")} autoComplete="address-line1" aria-invalid={!!errors?.line1} {...register(`${prefix}.line1`)} />
      </Field>
      <Field id={id("line2")} label="Apartment, suite (optional)" error={errors?.line2?.message} className="sm:col-span-2">
        <Input id={id("line2")} autoComplete="address-line2" {...register(`${prefix}.line2`)} />
      </Field>
      <Field id={id("city")} label="City" error={errors?.city?.message}>
        <Input id={id("city")} autoComplete="address-level2" aria-invalid={!!errors?.city} {...register(`${prefix}.city`)} />
      </Field>
      <Field id={id("region")} label="State / region" error={errors?.region?.message}>
        <Input id={id("region")} autoComplete="address-level1" {...register(`${prefix}.region`)} />
      </Field>
      <Field id={id("postal")} label="Postal code" error={errors?.postalCode?.message}>
        <Input id={id("postal")} autoComplete="postal-code" {...register(`${prefix}.postalCode`)} />
      </Field>
      <Field id={id("country")} label="Country code" hint="2 letters, e.g. US" error={errors?.countryCode?.message}>
        <Input id={id("country")} autoComplete="country" maxLength={2} aria-invalid={!!errors?.countryCode} {...register(`${prefix}.countryCode`)} />
      </Field>
      <Field id={id("phone")} label="Phone (optional)" error={errors?.phoneNumber?.message} className="sm:col-span-2">
        <Input id={id("phone")} type="tel" autoComplete="tel" {...register(`${prefix}.phoneNumber`)} />
      </Field>
    </div>
  );
}
