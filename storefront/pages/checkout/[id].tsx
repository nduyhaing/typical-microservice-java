import Link from 'next/link';
import { useRouter } from 'next/router';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import Spinner from 'react-bootstrap/Spinner';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../common/components/ErrorMessage';
import Layout from '../../common/components/Layout';
import { useFetch } from '../../common/hooks/useFetch';
import { ApiError } from '../../common/services/ApiClientService';
import { formatPrice } from '../../common/utils/format';
import { getUserAddresses } from '../../modules/address/services/AddressService';
import { LOGIN_URL } from '../../modules/auth/services/AuthenticationService';
import { couponErrorMessage } from '../../modules/order/models/Coupon';
import ShippingAddressPicker, {
  defaultShippingChoice,
  ShippingChoice,
} from '../../modules/order/components/ShippingAddressPicker';
import { AddressChoice, OrderPreview } from '../../modules/order/models/Order';
import { getCheckout, placeOrder, previewOrder } from '../../modules/order/services/OrderService';

const pending = () => new Promise<never>(() => {});

/** What the order service is told about the address, or undefined while a new address is not confirmed yet. */
function toAddressChoice(choice: ShippingChoice | undefined): AddressChoice | undefined {
  if (choice?.kind === 'saved') {
    return { shippingAddressId: choice.id };
  }
  return choice?.address ? { shippingAddressPostVm: choice.address } : undefined;
}

/** The server's `detail` of an error response, if any. */
function detailOf(error: unknown): string | undefined {
  const detail = error instanceof ApiError ? (error.body as { detail?: unknown } | null)?.detail : undefined;
  return typeof detail === 'string' ? detail : undefined;
}

const COUPON_UNAVAILABLE = 'The coupon could not be checked right now. Please try again later.';

function previewProblem(error: Error | undefined): string | undefined {
  if (!error) {
    return undefined;
  }
  const couponProblem = couponErrorMessage(error);
  if (couponProblem) {
    // e.g. the applied coupon was used up meanwhile: "Remove" prices the order without it
    return couponProblem;
  }
  if (error instanceof ApiError && error.status === 404) {
    return 'This address is no longer in your address book. Please choose another one.';
  }
  if (error instanceof ApiError && error.status === 400) {
    return detailOf(error) ?? 'Please check the address.';
  }
  return 'Tax could not be calculated right now. Please try again later.';
}

type SubmitError = { kind: 'retry' | 'login' | 'ordered' } | { kind: 'refused' | 'coupon'; detail: string };

/**
 * Checkout (slice 3c): items, a saved address (default preselected) or a new one, and the subtotal, tax and total
 * priced by the order service for that address — refreshed when the address changes; the browser never computes
 * tax (decisions.md 3c-46, 3c-48). A coupon code is applied by asking the server for a new preview with it; the
 * summary then shows the discount and the order is placed with the code (4-56). Cash on delivery (payments: slice 5).
 */
export default function CheckoutPage() {
  const router = useRouter();
  const id = typeof router.query.id === 'string' ? router.query.id : '';
  const { data: checkout, error, loading } = useFetch(() => (id ? getCheckout(id) : pending()), [id]);
  const { data: savedAddresses, error: addressesError } = useFetch(
    () => (id ? getUserAddresses() : pending()),
    [id]
  );
  const [picked, setPicked] = useState<ShippingChoice>();
  // until the customer picks, the default saved address (or a new one when there is none / the list failed)
  const choice: ShippingChoice | undefined =
    picked ?? (savedAddresses ? defaultShippingChoice(savedAddresses) : addressesError ? { kind: 'new' } : undefined);
  const addressChoice = toAddressChoice(choice);
  const [couponInput, setCouponInput] = useState('');
  const [appliedCode, setAppliedCode] = useState<string>();
  const [applying, setApplying] = useState(false);
  const [couponError, setCouponError] = useState<string>();
  const preview = useFetch<OrderPreview | undefined>(() => {
    if (!checkout || !addressChoice) {
      return Promise.resolve(undefined);
    }
    return appliedCode ? previewOrder(checkout.id, addressChoice, appliedCode) : previewOrder(checkout.id, addressChoice);
  }, [checkout?.id, addressChoice, appliedCode]);
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<SubmitError>();

  const notLoggedIn = error instanceof ApiError && (error.status === 401 || error.status === 403);
  const notFound = error instanceof ApiError && error.status === 404;
  const priced = !!addressChoice && !!preview.data && !preview.loading;
  const problem = addressChoice ? previewProblem(preview.error) : undefined;

  // the server verifies the code: it is applied only when a preview with it succeeds
  const handleApplyCoupon = async () => {
    const code = couponInput.trim();
    if (!checkout || !addressChoice || !code) {
      return;
    }
    setApplying(true);
    setCouponError(undefined);
    try {
      await previewOrder(checkout.id, addressChoice, code);
      setAppliedCode(code);
      setSubmitError(undefined);
    } catch (error) {
      setCouponError(couponErrorMessage(error) ?? COUPON_UNAVAILABLE);
    } finally {
      setApplying(false);
    }
  };

  const handleRemoveCoupon = () => {
    setAppliedCode(undefined);
    setCouponInput('');
    setCouponError(undefined);
    setSubmitError(undefined);
  };

  const handlePlaceOrder = async () => {
    if (!checkout || !addressChoice || !priced) {
      return;
    }
    setSubmitting(true);
    setSubmitError(undefined);
    try {
      const order = await placeOrder({
        checkoutId: checkout.id,
        ...addressChoice,
        note: note || undefined,
        paymentMethod: 'COD',
        ...(appliedCode ? { couponCode: appliedCode } : {}),
      });
      await router.push(`/my-orders?placed=${order.id}`);
    } catch (error) {
      // only suggest retrying when a retry can succeed
      const detail = detailOf(error);
      const couponProblem = couponErrorMessage(error);
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        setSubmitError({ kind: 'login' });
      } else if (couponProblem) {
        // e.g. the coupon was used up between the preview and the order
        setSubmitError({ kind: 'coupon', detail: `${couponProblem} Remove it to order without the discount.` });
      } else if (error instanceof ApiError && error.status === 400 && detail?.includes('already been ordered')) {
        setSubmitError({ kind: 'ordered' });
      } else if (error instanceof ApiError && (error.status === 400 || error.status === 404) && detail) {
        // e.g. "Not enough stock for Galaxy S24 Violet (product 25)", an address that is gone
        setSubmitError({ kind: 'refused', detail });
      } else {
        setSubmitError({ kind: 'retry' });
      }
      setSubmitting(false);
    }
  };

  return (
    <Layout title="Checkout">
      <h1 className="h3 mb-3">Checkout</h1>
      {loading && id && <Spinner animation="border" role="status" aria-label="Loading" />}
      {notLoggedIn && <a href={LOGIN_URL}>Log in to check out</a>}
      {notFound && <p>This checkout does not exist or has already been ordered.</p>}
      {error && !notLoggedIn && !notFound && <ErrorMessage />}
      {checkout && (
        <Row className="g-4">
          <Col lg={7}>
            <h2 className="h5">Shipping address</h2>
            {!choice && <Spinner animation="border" size="sm" role="status" aria-label="Loading addresses" />}
            {choice && (
              <ShippingAddressPicker
                addresses={savedAddresses ?? []}
                choice={choice}
                onChange={(next) => {
                  setPicked(next);
                  setSubmitError(undefined);
                }}
              />
            )}
            <Form.Group controlId="order-note" className="mb-3">
              <Form.Label>Note (optional)</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                maxLength={255}
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
            </Form.Group>
            <h2 className="h5">Payment</h2>
            <p>Cash on delivery</p>
            {submitError?.kind === 'retry' && (
              <Alert variant="danger">Your order could not be placed. Please try again.</Alert>
            )}
            {submitError?.kind === 'login' && (
              <Alert variant="warning">
                Your session has expired. <a href={LOGIN_URL}>Log in again</a>
              </Alert>
            )}
            {submitError?.kind === 'ordered' && (
              <Alert variant="warning">
                This checkout has already been ordered. <Link href="/my-orders">See my orders</Link>
              </Alert>
            )}
            {submitError?.kind === 'coupon' && <Alert variant="warning">{submitError.detail}</Alert>}
            {submitError?.kind === 'refused' && (
              <Alert variant="warning">
                {submitError.detail} <Link href="/cart">Back to the cart</Link>
              </Alert>
            )}
            <Button onClick={handlePlaceOrder} disabled={!priced || submitting}>
              Place order
            </Button>
          </Col>
          <Col lg={5}>
            <h2 className="h5">Your order</h2>
            <Table size="sm">
              <tbody>
                {checkout.checkoutItemVms.map((item, index) => {
                  // the server's line amount from the preview (same order as the checkout lines); never price * qty
                  const line = preview.data?.items[index];
                  const amount = line && line.productId === item.productId ? line.subtotal : undefined;
                  return (
                    <tr key={item.id}>
                      <td>{item.productName}</td>
                      <td className="text-end text-nowrap">× {item.quantity}</td>
                      <td className="text-end" data-testid={`checkout-line-${item.id}`}>
                        {amount === undefined ? '—' : formatPrice(amount)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={2}>Subtotal</td>
                  <td className="text-end" data-testid="checkout-subtotal">
                    {formatPrice(preview.data?.subtotal ?? checkout.totalAmount)}
                  </td>
                </tr>
                {priced && preview.data && preview.data.discount > 0 && (
                  <tr>
                    <td colSpan={2}>Discount{preview.data.couponCode ? ` (${preview.data.couponCode})` : ''}</td>
                    <td className="text-end" data-testid="checkout-discount">
                      −{formatPrice(preview.data.discount)}
                    </td>
                  </tr>
                )}
                <tr>
                  <td colSpan={2}>Tax</td>
                  <td className="text-end" data-testid="checkout-tax">
                    {priced && preview.data ? formatPrice(preview.data.totalTax) : '—'}
                  </td>
                </tr>
                <tr>
                  <th colSpan={2}>Total</th>
                  <th className="text-end" data-testid="checkout-total">
                    {priced && preview.data ? formatPrice(preview.data.total) : '—'}
                  </th>
                </tr>
              </tfoot>
            </Table>
            {appliedCode ? (
              <p className="d-flex align-items-center gap-2" data-testid="coupon-applied">
                <span>Coupon {preview.data?.couponCode ?? appliedCode} applied</span>
                <Button variant="link" size="sm" className="p-0" onClick={handleRemoveCoupon}>
                  Remove
                </Button>
              </p>
            ) : (
              <Form.Group controlId="coupon-code" className="mb-3">
                <Form.Label>Coupon code</Form.Label>
                <div className="d-flex gap-2">
                  <Form.Control
                    value={couponInput}
                    maxLength={100}
                    onChange={(event) => {
                      setCouponInput(event.target.value);
                      setCouponError(undefined);
                    }}
                  />
                  <Button
                    variant="outline-secondary"
                    onClick={handleApplyCoupon}
                    disabled={!couponInput.trim() || !addressChoice || applying}
                  >
                    Apply
                  </Button>
                </div>
                {couponError && <Form.Text className="text-danger">{couponError}</Form.Text>}
              </Form.Group>
            )}
            {addressChoice && preview.loading && (
              <Spinner animation="border" size="sm" role="status" aria-label="Calculating tax" />
            )}
            {!addressChoice && <p className="small text-muted">Choose a shipping address to see the tax and total.</p>}
            {problem && <ErrorMessage message={problem} />}
            <p className="small text-muted">No shipping fee is charged yet.</p>
          </Col>
        </Row>
      )}
    </Layout>
  );
}
