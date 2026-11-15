import Link from 'next/link';
import { useRouter } from 'next/router';
import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import Spinner from 'react-bootstrap/Spinner';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../common/components/ErrorMessage';
import Layout from '../common/components/Layout';
import { useFetch } from '../common/hooks/useFetch';
import { ApiError } from '../common/services/ApiClientService';
import { formatPrice } from '../common/utils/format';
import { LOGIN_URL } from '../modules/auth/services/AuthenticationService';
import { CartLine } from '../modules/cart/models/CartItem';
import { getCartLines, removeItem, updateQuantity } from '../modules/cart/services/CartService';
import { createCheckout } from '../modules/order/services/OrderService';

/** The logged-in customer's cart (slice 2b; checkout 2c; stock limits and messages 3c). */
export default function CartPage() {
  const router = useRouter();
  const [version, setVersion] = useState(0);
  const [checkingOut, setCheckingOut] = useState(false);
  // true: generic error; string: the server's explanation (e.g. "Only 3 of Galaxy S24 Violet are in stock")
  const [actionError, setActionError] = useState<boolean | string>(false);
  const { data: lines, error, loading } = useFetch(getCartLines, [version]);
  const notLoggedIn = error instanceof ApiError && (error.status === 401 || error.status === 403);

  const change = async (action: () => Promise<void>) => {
    try {
      await action();
      setActionError(false);
    } catch (error) {
      const detail = error instanceof ApiError ? (error.body as { detail?: unknown } | null)?.detail : undefined;
      setActionError(error instanceof ApiError && error.status === 400 && typeof detail === 'string' ? detail : true);
    }
    setVersion((current) => current + 1);
  };

  const decrease = (line: CartLine) =>
    change(() => (line.quantity <= 1 ? removeItem(line.productId) : updateQuantity(line.productId, line.quantity - 1)));
  const increase = (line: CartLine) => change(() => updateQuantity(line.productId, line.quantity + 1));
  const canCheckout = !!lines && lines.length > 0 && lines.every((line) => line.available) && !checkingOut;

  const checkout = async () => {
    if (!lines) {
      return;
    }
    setCheckingOut(true);
    try {
      const created = await createCheckout(lines.map((line) => ({ productId: line.productId, quantity: line.quantity })));
      await router.push(`/checkout/${created.id}`);
    } catch {
      setActionError(true);
      setCheckingOut(false);
    }
  };

  const total = (lines ?? [])
    .filter((line) => line.available)
    .reduce((sum, line) => sum + line.price * line.quantity, 0);

  return (
    <Layout title="Cart">
      <h1 className="h3 mb-3">Your cart</h1>
      {loading && <Spinner animation="border" role="status" aria-label="Loading" />}
      {notLoggedIn && <a href={LOGIN_URL}>Log in to see your cart</a>}
      {typeof actionError === 'string' && <ErrorMessage message={actionError} />}
      {(actionError === true || (error && !notLoggedIn)) && <ErrorMessage />}
      {lines && lines.length === 0 && (
        <p>
          Your cart is empty. <Link href="/products">Browse products</Link>
        </p>
      )}
      {lines && lines.length > 0 && (
        <Table responsive className="align-middle">
          <thead>
            <tr>
              <th>Product</th>
              <th className="text-end">Price</th>
              <th className="text-center">Quantity</th>
              <th className="text-end">Total</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {lines.map((line) =>
              line.available ? (
                <tr key={line.productId}>
                  <td>
                    <Link href={`/products/${line.slug}`}>{line.name}</Link>
                  </td>
                  <td className="text-end">{formatPrice(line.price)}</td>
                  <td className="text-center text-nowrap">
                    <Button size="sm" variant="outline-secondary" aria-label={`Decrease ${line.name}`}
                      onClick={() => decrease(line)}>
                      −
                    </Button>
                    <span className="mx-2" data-testid={`quantity-${line.productId}`}>
                      {line.quantity}
                    </span>
                    <Button size="sm" variant="outline-secondary" aria-label={`Increase ${line.name}`}
                      disabled={line.stockQuantity !== undefined && line.quantity >= line.stockQuantity}
                      onClick={() => increase(line)}>
                      +
                    </Button>
                    {line.stockQuantity !== undefined && line.quantity > line.stockQuantity && (
                      <div className="small text-danger" data-testid={`stock-${line.productId}`}>
                        {line.stockQuantity === 0 ? 'Out of stock' : `Only ${line.stockQuantity} in stock`}
                      </div>
                    )}
                  </td>
                  <td className="text-end" data-testid={`line-total-${line.productId}`}>
                    {formatPrice(line.price * line.quantity)}
                  </td>
                  <td className="text-end">
                    <Button size="sm" variant="link" className="text-danger" aria-label={`Remove ${line.name}`}
                      onClick={() => change(() => removeItem(line.productId))}>
                      Remove
                    </Button>
                  </td>
                </tr>
              ) : (
                <tr key={line.productId} className="text-muted">
                  <td>{line.name}</td>
                  <td className="text-end">—</td>
                  <td className="text-center">{line.quantity}</td>
                  <td className="text-end">No longer available</td>
                  <td className="text-end">
                    <Button size="sm" variant="link" className="text-danger" aria-label={`Remove ${line.name}`}
                      onClick={() => change(() => removeItem(line.productId))}>
                      Remove
                    </Button>
                  </td>
                </tr>
              )
            )}
          </tbody>
          <tfoot>
            <tr>
              <th colSpan={3} className="text-end">
                Total
              </th>
              <th className="text-end" data-testid="cart-total">
                {formatPrice(total)}
              </th>
              <th />
            </tr>
          </tfoot>
        </Table>
      )}
      {lines && lines.length > 0 && (
        <div className="text-end">
          {!lines.every((line) => line.available) && (
            <p className="small text-muted">Remove the items that are no longer available to check out.</p>
          )}
          <Button onClick={checkout} disabled={!canCheckout}>
            Checkout
          </Button>
        </div>
      )}
    </Layout>
  );
}
