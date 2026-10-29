"use client";

import { Elements, PaymentElement, useElements, useStripe } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";
import { CheckCircle2, CreditCard } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { ErrorState, LoadingState } from "@/components/common/states";
import { useApi } from "@/hooks/use-api";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { errorMessage } from "@/lib/api/client";
import { orderApi } from "@/lib/api/endpoints";
import type { OrderDetail, PaymentStart } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";

const publishableKey = process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY;
const stripePromise = publishableKey ? loadStripe(publishableKey) : null;

/**
 * Payment step. The order's paid status always comes from the backend, which learns it from the
 * provider (signed webhook or server-to-server lookup), never from this page.
 */
export function PaymentPanel({ orderId }: { orderId: number }) {
  const params = useSearchParams();
  const returnedFromProvider = params.has("redirect_status");
  const cancelledAtProvider = params.get("redirect_status") === "cancelled";
  const [message, setMessage] = useState<string | null>(
    cancelledAtProvider ? "Payment was cancelled. You can try again below." : null,
  );
  const { data, error, reload, setData } = useApi(async () => {
    // Returning from a provider redirect: ask the backend to verify with the provider directly.
    const order = returnedFromProvider ? await orderApi.refreshPayment(orderId) : await orderApi.get(orderId);
    const payable = order.status !== "CANCELLED" && (order.paymentStatus === "PENDING" || order.paymentStatus === "FAILED");
    const payment: PaymentStart | null = payable ? await orderApi.startPayment(orderId) : null;
    if (payment?.status === "SUCCEEDED") {
      // The provider confirmed a payment the webhook had not delivered yet.
      return { order: await orderApi.get(orderId), payment: null };
    }
    return { order, payment };
  }, [orderId, returnedFromProvider]);

  if (error) {
    return <ErrorState message={error} onRetry={reload} />;
  }
  if (!data) {
    return <LoadingState label="Loading your order…" />;
  }
  const { order, payment } = data;

  if (order.paymentStatus === "PAID") {
    return (
      <Card className="mx-auto max-w-lg text-center">
        <CardHeader>
          <CheckCircle2 className="mx-auto size-10 text-emerald-600" aria-hidden />
          <CardTitle>Thank you! Your order is confirmed.</CardTitle>
          <CardDescription>Order {order.orderNumber} · {formatMoney(order.grandTotal, order.currency)}</CardDescription>
        </CardHeader>
        <CardContent className="flex justify-center gap-2">
          <Link href={`/account/orders/${order.id}`} className={buttonVariants()}>View order</Link>
          <Link href="/products" className={buttonVariants({ variant: "outline" })}>Continue shopping</Link>
        </CardContent>
      </Card>
    );
  }
  if (order.status === "CANCELLED") {
    return <ErrorState message={`Order ${order.orderNumber} was cancelled${order.cancelReason ? `: ${order.cancelReason}` : "."}`} />;
  }

  return (
    <div className="mx-auto max-w-lg space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><CreditCard className="size-5" /> Payment</CardTitle>
          <CardDescription>Order {order.orderNumber} · Total {formatMoney(order.grandTotal, order.currency)}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {order.paymentStatus === "FAILED" && (
            <Alert variant="destructive"><AlertDescription>
              {order.payments[0]?.failureMessage ?? "Your last payment attempt failed."} Please try again.
            </AlertDescription></Alert>
          )}
          {message && <Alert><AlertDescription>{message}</AlertDescription></Alert>}
          {!payment ? <LoadingState label="Preparing payment…" /> : payment.provider === "MOCK" ? (
            <MockPayment orderId={orderId} onDone={(o) => {
              setData({ order: o, payment: null });
              if (o.paymentStatus === "FAILED") {
                reload(); // start a fresh payment attempt
              }
            }} />
          ) : payment.provider === "PAYWAY" && payment.checkout ? (
            <PayWayPayment checkout={payment.checkout} />
          ) : payment.clientSecret && stripePromise ? (
            <Elements stripe={stripePromise} options={{ clientSecret: payment.clientSecret }}>
              <StripePayment orderId={orderId} onMessage={setMessage} />
            </Elements>
          ) : (
            <ErrorState message="Online payment is not configured." />
          )}
        </CardContent>
      </Card>
      <p className="text-center text-xs text-muted-foreground">
        Card details are entered directly with our payment provider and never touch our servers.
      </p>
    </div>
  );
}

/** Development provider: simulates the provider's success/decline signal on the server. */
function MockPayment({ orderId, onDone }: { orderId: number; onDone: (o: OrderDetail) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async (success: boolean) => {
    setBusy(true);
    setError(null);
    try {
      onDone(await orderApi.mockConfirm(orderId, success));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="space-y-3">
      <Alert><AlertDescription>Test mode: no real payment is taken.</AlertDescription></Alert>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button className="flex-1" disabled={busy} onClick={() => run(true)}>Pay now (test card)</Button>
        <Button variant="outline" className="flex-1" disabled={busy} onClick={() => run(false)}>Simulate declined card</Button>
      </div>
    </div>
  );
}

function StripePayment({ orderId, onMessage }: { orderId: number; onMessage: (m: string) => void }) {
  const stripe = useStripe();
  const elements = useElements();
  const [busy, setBusy] = useState(false);
  return (
    <form className="space-y-4" onSubmit={async (e) => {
      e.preventDefault();
      if (!stripe || !elements) {
        return;
      }
      setBusy(true);
      const { error } = await stripe.confirmPayment({
        elements,
        confirmParams: { return_url: `${window.location.origin}/checkout/payment/${orderId}` },
      });
      // Only reached on immediate errors; otherwise Stripe redirects back to return_url.
      if (error) {
        onMessage(error.message ?? "Payment failed.");
      }
      setBusy(false);
    }}>
      <PaymentElement />
      <Button type="submit" className="w-full" disabled={!stripe || busy}>{busy ? "Processing…" : "Pay now"}</Button>
    </form>
  );
}

/**
 * ABA PayWay hosted checkout (cards, ABA Pay, KHQR). The backend signs the form; submitting it
 * takes the customer to PayWay, which sends them back to this page when they finish or cancel.
 */
function PayWayPayment({ checkout }: { checkout: NonNullable<PaymentStart["checkout"]> }) {
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    // Coming back with the browser's Back button restores this page from cache: re-enable the button.
    const reset = (e: PageTransitionEvent) => e.persisted && setBusy(false);
    window.addEventListener("pageshow", reset);
    return () => window.removeEventListener("pageshow", reset);
  }, []);
  return (
    <form method="post" action={checkout.actionUrl} encType="multipart/form-data" className="space-y-3"
      onSubmit={() => setBusy(true)}>
      {Object.entries(checkout.fields).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <p className="text-sm text-muted-foreground">
        You will be redirected to ABA PayWay to pay by card, ABA Pay or KHQR.
      </p>
      <Button type="submit" className="w-full" disabled={busy}>
        {busy ? "Redirecting…" : "Pay with ABA PayWay"}
      </Button>
    </form>
  );
}
