import Link from 'next/link';
import { useRouter } from 'next/router';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Spinner from 'react-bootstrap/Spinner';
import ErrorMessage from '../common/components/ErrorMessage';
import Layout from '../common/components/Layout';
import { useFetch } from '../common/hooks/useFetch';
import { ApiError } from '../common/services/ApiClientService';
import { formatPrice } from '../common/utils/format';
import { LOGIN_URL } from '../modules/auth/services/AuthenticationService';
import { Order, orderStatusLabel } from '../modules/order/models/Order';
import { paymentStatusLabel } from '../modules/payment/models/Payment';
import { cancelOrder, getMyOrders } from '../modules/order/services/OrderService';

const CANCEL_FAILED = 'The order could not be cancelled right now. Please try again.';

/** The order service's reason for a refused cancel (400/404 ErrorVm `detail`), else a generic text. */
function cancelError(error: unknown): string {
  if (error instanceof ApiError && (error.status === 400 || error.status === 404)) {
    const detail = (error.body as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
  }
  return CANCEL_FAILED;
}

/** Not paid yet and still open: the customer can (re)start paying it (decisions.md 5-19). */
function isPayable(order: Order): boolean {
  return order.paymentStatus === 'PENDING' && ['PENDING', 'PENDING_PAYMENT', 'ACCEPTED'].includes(order.orderStatus);
}

/** Badge colour per status. */
function statusVariant(status: string): { bg: string; text?: 'dark' } {
  switch (status) {
    case 'PAID':
    case 'COMPLETED':
      return { bg: 'success' };
    case 'SHIPPING':
    case 'ACCEPTED':
      return { bg: 'info', text: 'dark' };
    case 'CANCELLED':
    case 'REJECT':
    case 'REFUND':
      return { bg: 'secondary' };
    default:
      return { bg: 'warning', text: 'dark' };
  }
}

/** The customer's orders, newest first; an unpaid order can be cancelled (decisions.md 5-8). */
export default function MyOrdersPage() {
  const router = useRouter();
  const placed = typeof router.query.placed === 'string' ? router.query.placed : undefined;
  const { data: orders, error, loading } = useFetch(getMyOrders);
  const notLoggedIn = error instanceof ApiError && (error.status === 401 || error.status === 403);
  const [changed, setChanged] = useState<Record<number, Order>>({});
  const [cancelling, setCancelling] = useState<number>();
  const [notice, setNotice] = useState<string>();
  const [cancelFailure, setCancelFailure] = useState<string>();

  async function cancel(order: Order) {
    if (!window.confirm(`Cancel order #${order.id}? This cannot be undone.`)) {
      return;
    }
    setCancelling(order.id);
    setNotice(undefined);
    setCancelFailure(undefined);
    try {
      const cancelled = await cancelOrder(order.id);
      setChanged((current) => ({ ...current, [order.id]: cancelled }));
      setNotice(`Order #${order.id} has been cancelled.`);
    } catch (e) {
      setCancelFailure(cancelError(e));
    } finally {
      setCancelling(undefined);
    }
  }

  return (
    <Layout title="My orders">
      <h1 className="h3 mb-3">My orders</h1>
      {notice && <Alert variant="success">{notice}</Alert>}
      {cancelFailure && <ErrorMessage message={cancelFailure} />}
      {placed && <Alert variant="success">Thank you! Your order #{placed} has been placed.</Alert>}
      {loading && <Spinner animation="border" role="status" aria-label="Loading" />}
      {notLoggedIn && <a href={LOGIN_URL}>Log in to see your orders</a>}
      {error && !notLoggedIn && <ErrorMessage />}
      {orders && orders.length === 0 && (
        <p>
          You have no orders yet. <Link href="/products">Browse products</Link>
        </p>
      )}
      {orders?.map((listed) => changed[listed.id] ?? listed).map((order) => (
        <Card key={order.id} className="mb-3">
          <Card.Header className="d-flex justify-content-between align-items-center">
            <span>
              <strong>Order #{order.id}</strong>
              <span className="text-muted ms-2 small">{new Date(order.createdOn).toLocaleString()}</span>
            </span>
            <Badge {...statusVariant(order.orderStatus)}>{orderStatusLabel(order.orderStatus)}</Badge>
          </Card.Header>
          <Card.Body>
            <ul className="mb-2">
              {order.orderItemVms.map((item) => (
                <li key={item.productId}>
                  {item.productName} × {item.quantity}
                </li>
              ))}
            </ul>
            {!!order.tax && (
              <div className="text-end small text-muted" data-testid={`order-tax-${order.id}`}>
                incl. tax {formatPrice(order.tax)}
              </div>
            )}
            <div className="text-end fw-bold" data-testid={`order-total-${order.id}`}>
              {formatPrice(order.totalPrice)}
            </div>
            <div className="text-end small text-muted">Payment: {paymentStatusLabel(order.paymentStatus)}</div>
            {isPayable(order) && (
              <div className="text-end mt-2">
                <Link href={`/checkout/pay/${order.id}`} className="btn btn-sm btn-primary">
                  Pay now
                </Link>
              </div>
            )}
            {order.cancellable && (
              <div className="text-end mt-2">
                <Button size="sm" variant="outline-danger" disabled={cancelling === order.id} onClick={() => cancel(order)}>
                  Cancel order
                </Button>
              </div>
            )}
          </Card.Body>
        </Card>
      ))}
    </Layout>
  );
}
