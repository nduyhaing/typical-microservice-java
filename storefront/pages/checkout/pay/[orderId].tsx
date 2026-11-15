import Link from 'next/link';
import { useRouter } from 'next/router';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useFetch } from '../../../common/hooks/useFetch';
import { ApiError } from '../../../common/services/ApiClientService';
import { formatPrice } from '../../../common/utils/format';
import { LOGIN_URL } from '../../../modules/auth/services/AuthenticationService';
import { getMyOrders } from '../../../modules/order/services/OrderService';
import { getPaymentProviders, initPayment } from '../../../modules/payment/services/PaymentService';

const pending = () => new Promise<never>(() => {});
const PAYABLE = ['PENDING', 'PENDING_PAYMENT', 'ACCEPTED'];

/** The server's `detail` of a refused payment, else a generic text. */
function payError(error: unknown): string {
  if (error instanceof ApiError && (error.status === 400 || error.status === 404)) {
    const detail = (error.body as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
  }
  if (error instanceof ApiError && error.status === 503) {
    const detail = (error.body as { detail?: unknown } | null)?.detail;
    return typeof detail === 'string' && detail.trim() ? detail : 'Payments are not available right now.';
  }
  return 'The payment could not be started. Please try again.';
}

/**
 * The pay step after placing an order (decisions.md 5-19): choose one of the available providers. Cash on delivery
 * records the payment and says so; PayPal sends the browser to PayPal's approval page, which comes back to
 * /checkout/payment-return. The amount is never sent: the payment service reads it from the order.
 */
export default function PayPage() {
  const router = useRouter();
  const orderId = typeof router.query.orderId === 'string' ? Number(router.query.orderId) : NaN;
  const valid = Number.isInteger(orderId) && orderId > 0;
  const orders = useFetch(() => (valid ? getMyOrders() : pending()), [orderId]);
  const providers = useFetch(() => (valid ? getPaymentProviders() : pending()), [orderId]);
  const [picked, setPicked] = useState<string>();
  const [paying, setPaying] = useState(false);
  const [codDone, setCodDone] = useState(false);
  const [failure, setFailure] = useState<string>();

  const order = orders.data?.find((candidate) => candidate.id === orderId);
  const method = picked ?? providers.data?.[0]?.id;
  const notLoggedIn = orders.error instanceof ApiError && (orders.error.status === 401 || orders.error.status === 403);
  const payable = !!order && order.paymentStatus !== 'COMPLETED' && PAYABLE.includes(order.orderStatus);

  async function pay() {
    if (!order || !method) {
      return;
    }
    setPaying(true);
    setFailure(undefined);
    try {
      const result = await initPayment(order.id, method);
      if (result.redirectUrl) {
        window.location.assign(result.redirectUrl);
        return;
      }
      setCodDone(true);
    } catch (error) {
      setFailure(payError(error));
    }
    setPaying(false);
  }

  return (
    <Layout title="Payment">
      <h1 className="h3 mb-3">Payment</h1>
      {(orders.loading || providers.loading) && valid && (
        <Spinner animation="border" role="status" aria-label="Loading" />
      )}
      {notLoggedIn && <a href={LOGIN_URL}>Log in to pay your order</a>}
      {orders.error && !notLoggedIn && <ErrorMessage />}
      {orders.data && !order && <p>This order does not exist.</p>}
      {order && !payable && !codDone && (
        <p>
          Order #{order.id} is {order.paymentStatus === 'COMPLETED' ? 'already paid' : 'not payable'}.{' '}
          <Link href="/my-orders">My orders</Link>
        </p>
      )}
      {providers.error && order && payable && (
        <Alert variant="warning">
          Payments are not available right now. Your order is saved: you can pay it later from{' '}
          <Link href="/my-orders">My orders</Link>.
        </Alert>
      )}
      {codDone && order && (
        <Alert variant="success">
          Order placed — pay on delivery. Order #{order.id} ({formatPrice(order.totalPrice)}) is paid in cash when it is
          delivered. <Link href="/my-orders">My orders</Link>
        </Alert>
      )}
      {order && payable && providers.data && !codDone && (
        <>
          <p>
            Order #{order.id}: <strong data-testid="pay-total">{formatPrice(order.totalPrice)}</strong>
          </p>
          {providers.data.length === 0 && <p>No payment method is available right now.</p>}
          <Form>
            {providers.data.map((provider) => (
              <Form.Check
                key={provider.id}
                type="radio"
                id={`provider-${provider.id}`}
                name="payment-provider"
                label={provider.name}
                checked={method === provider.id}
                onChange={() => setPicked(provider.id)}
              />
            ))}
          </Form>
          {failure && <ErrorMessage message={failure} />}
          <Button className="mt-3" onClick={pay} disabled={!method || paying}>
            Pay
          </Button>
        </>
      )}
    </Layout>
  );
}
