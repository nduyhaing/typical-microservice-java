import Link from 'next/link';
import { useRouter } from 'next/router';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import Card from 'react-bootstrap/Card';
import Spinner from 'react-bootstrap/Spinner';
import ErrorMessage from '../common/components/ErrorMessage';
import Layout from '../common/components/Layout';
import { useFetch } from '../common/hooks/useFetch';
import { ApiError } from '../common/services/ApiClientService';
import { formatPrice } from '../common/utils/format';
import { LOGIN_URL } from '../modules/auth/services/AuthenticationService';
import { orderStatusLabel } from '../modules/order/models/Order';
import { getMyOrders } from '../modules/order/services/OrderService';

/** The customer's orders, newest first. */
export default function MyOrdersPage() {
  const router = useRouter();
  const placed = typeof router.query.placed === 'string' ? router.query.placed : undefined;
  const { data: orders, error, loading } = useFetch(getMyOrders);
  const notLoggedIn = error instanceof ApiError && (error.status === 401 || error.status === 403);

  return (
    <Layout title="My orders">
      <h1 className="h3 mb-3">My orders</h1>
      {placed && <Alert variant="success">Thank you! Your order #{placed} has been placed.</Alert>}
      {loading && <Spinner animation="border" role="status" aria-label="Loading" />}
      {notLoggedIn && <a href={LOGIN_URL}>Log in to see your orders</a>}
      {error && !notLoggedIn && <ErrorMessage />}
      {orders && orders.length === 0 && (
        <p>
          You have no orders yet. <Link href="/products">Browse products</Link>
        </p>
      )}
      {orders?.map((order) => (
        <Card key={order.id} className="mb-3">
          <Card.Header className="d-flex justify-content-between align-items-center">
            <span>
              <strong>Order #{order.id}</strong>
              <span className="text-muted ms-2 small">{new Date(order.createdOn).toLocaleString()}</span>
            </span>
            <Badge bg="warning" text="dark">
              {orderStatusLabel(order.orderStatus)}
            </Badge>
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
          </Card.Body>
        </Card>
      ))}
    </Layout>
  );
}
