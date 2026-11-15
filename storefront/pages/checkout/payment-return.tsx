import Link from 'next/link';
import { useRouter } from 'next/router';
import { useEffect, useRef, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Spinner from 'react-bootstrap/Spinner';
import Layout from '../../common/components/Layout';
import { ApiError } from '../../common/services/ApiClientService';
import { cancelPayment, capturePayment } from '../../modules/payment/services/PaymentService';

type State =
  | { kind: 'working' }
  | { kind: 'paid'; orderId: number }
  | { kind: 'cancelled'; orderId?: number }
  | { kind: 'failed'; detail: string }
  | { kind: 'retry' };

function detailOf(error: unknown): string | undefined {
  const detail = error instanceof ApiError ? (error.body as { detail?: unknown } | null)?.detail : undefined;
  return typeof detail === 'string' && detail.trim() ? detail : undefined;
}

/** Captures server-side and maps the answer to what the page shows (no state here: the callers set it). */
async function captureOutcome(paypalToken: string): Promise<State> {
  try {
    const result = await capturePayment(paypalToken);
    return result.paymentStatus === 'COMPLETED'
      ? { kind: 'paid', orderId: result.orderId }
      : { kind: 'failed', detail: 'PayPal did not complete the payment.' };
  } catch (error) {
    if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
      return { kind: 'failed', detail: detailOf(error) ?? 'The payment could not be completed.' };
    }
    return { kind: 'retry' };
  }
}

/**
 * PayPal's return (YAS complete-payment page): PayPal appends `?token=<PayPal order id>`. The payment service
 * captures and verifies the payment server-side; this page only shows the outcome (decisions.md 5-19). The cancel URL
 * adds `cancelled=true`: the pending payment is cancelled and the buyer can pick another way to pay.
 */
export default function PaymentReturnPage() {
  const router = useRouter();
  const token = typeof router.query.token === 'string' ? router.query.token : undefined;
  const cancelled = router.query.cancelled === 'true';
  const [state, setState] = useState<State>({ kind: 'working' });
  const started = useRef<string | undefined>(undefined);
  const ready = router.isReady !== false;

  useEffect(() => {
    if (!ready || !token) {
      return;
    }
    const key = `${token}:${cancelled}`;
    if (started.current === key) {
      return;
    }
    started.current = key;
    if (cancelled) {
      cancelPayment(token)
        .then((result) => setState({ kind: 'cancelled', orderId: result.orderId }))
        .catch(() => setState({ kind: 'cancelled' }));
    } else {
      void captureOutcome(token).then(setState);
    }
  }, [ready, token, cancelled]);

  return (
    <Layout title="Payment">
      <h1 className="h3 mb-3">Payment</h1>
      {ready && !token && (
        <p>
          No payment to complete. <Link href="/my-orders">My orders</Link>
        </p>
      )}
      {token && state.kind === 'working' && <Spinner animation="border" role="status" aria-label="Completing the payment" />}
      {state.kind === 'paid' && (
        <Alert variant="success">
          Payment successful: order #{state.orderId} is paid. <Link href="/my-orders">My orders</Link>
        </Alert>
      )}
      {state.kind === 'cancelled' && (
        <Alert variant="warning">
          Payment cancelled at PayPal.{' '}
          {state.orderId ? (
            <Link href={`/checkout/pay/${state.orderId}`}>Choose another way to pay</Link>
          ) : (
            <Link href="/my-orders">My orders</Link>
          )}
        </Alert>
      )}
      {state.kind === 'failed' && (
        <Alert variant="danger">
          <p className="mb-1">Payment failed.</p>
          <p className="mb-1">{state.detail}</p>
          <Link href="/my-orders">My orders</Link>
        </Alert>
      )}
      {state.kind === 'retry' && token && (
        <Alert variant="warning">
          The payment could not be completed right now.{' '}
          <Button
            size="sm"
            onClick={() => {
              setState({ kind: 'working' });
              void captureOutcome(token).then(setState);
            }}
          >
            Try again
          </Button>
        </Alert>
      )}
    </Layout>
  );
}
