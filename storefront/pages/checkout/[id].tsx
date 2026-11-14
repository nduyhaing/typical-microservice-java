import Link from 'next/link';
import { useRouter } from 'next/router';
import { FormEvent, useState } from 'react';
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
import { LOGIN_URL } from '../../modules/auth/services/AuthenticationService';
import { OrderAddress } from '../../modules/order/models/Order';
import { getCheckout, placeOrder } from '../../modules/order/services/OrderService';

const EMPTY_ADDRESS: OrderAddress = { contactName: '', phone: '', addressLine1: '', city: '', zipCode: '', countryName: '' };
const REQUIRED: (keyof OrderAddress)[] = ['contactName', 'phone', 'addressLine1', 'city', 'countryName'];

const FIELDS: { key: keyof OrderAddress; label: string; md: number }[] = [
  { key: 'contactName', label: 'Full name', md: 6 },
  { key: 'phone', label: 'Phone', md: 6 },
  { key: 'addressLine1', label: 'Address', md: 12 },
  { key: 'city', label: 'City', md: 5 },
  { key: 'zipCode', label: 'Zip code', md: 3 },
  { key: 'countryName', label: 'Country', md: 4 },
];

/**
 * Checkout (slice 2c): items and the server-computed total, a typed-in shipping address (saved addresses come with
 * slice 3) and cash on delivery (payments: slice 5).
 */
export default function CheckoutPage() {
  const router = useRouter();
  const id = typeof router.query.id === 'string' ? router.query.id : '';
  const { data: checkout, error, loading } = useFetch(
    () => (id ? getCheckout(id) : new Promise<never>(() => {})),
    [id]
  );
  const [address, setAddress] = useState<OrderAddress>(EMPTY_ADDRESS);
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<'retry' | 'login' | 'ordered'>();

  const notLoggedIn = error instanceof ApiError && (error.status === 401 || error.status === 403);
  const notFound = error instanceof ApiError && error.status === 404;
  const complete = REQUIRED.every((field) => (address[field] ?? '').trim() !== '');

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!checkout || !complete) {
      return;
    }
    setSubmitting(true);
    setSubmitError(undefined);
    try {
      const order = await placeOrder({
        checkoutId: checkout.id,
        shippingAddressPostVm: address,
        note: note || undefined,
        paymentMethod: 'COD',
      });
      await router.push(`/my-orders?placed=${order.id}`);
    } catch (error) {
      // only suggest retrying when a retry can succeed
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        setSubmitError('login');
      } else if (error instanceof ApiError && error.status === 400) {
        setSubmitError('ordered');
      } else {
        setSubmitError('retry');
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
            <Form onSubmit={handleSubmit}>
              <h2 className="h5">Shipping address</h2>
              <Row className="g-3 mb-3">
                {FIELDS.map((field) => (
                  <Col md={field.md} key={field.key}>
                    <Form.Group controlId={`address-${field.key}`}>
                      <Form.Label>{field.label}</Form.Label>
                      <Form.Control
                        value={address[field.key] ?? ''}
                        required={REQUIRED.includes(field.key)}
                        onChange={(event) => setAddress({ ...address, [field.key]: event.target.value })}
                      />
                    </Form.Group>
                  </Col>
                ))}
              </Row>
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
              {submitError === 'retry' && (
                <Alert variant="danger">Your order could not be placed. Please try again.</Alert>
              )}
              {submitError === 'login' && (
                <Alert variant="warning">
                  Your session has expired. <a href={LOGIN_URL}>Log in again</a>
                </Alert>
              )}
              {submitError === 'ordered' && (
                <Alert variant="warning">
                  This checkout has already been ordered. <Link href="/my-orders">See my orders</Link>
                </Alert>
              )}
              <Button type="submit" disabled={!complete || submitting}>
                Place order
              </Button>
            </Form>
          </Col>
          <Col lg={5}>
            <h2 className="h5">Your order</h2>
            <Table size="sm">
              <tbody>
                {checkout.checkoutItemVms.map((item) => (
                  <tr key={item.id}>
                    <td>{item.productName}</td>
                    <td className="text-end text-nowrap">× {item.quantity}</td>
                    <td className="text-end">{formatPrice(item.productPrice * item.quantity)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th colSpan={2}>Total</th>
                  <th className="text-end" data-testid="checkout-total">
                    {formatPrice(checkout.totalAmount)}
                  </th>
                </tr>
              </tfoot>
            </Table>
            <p className="small text-muted">No tax or shipping fee is charged yet.</p>
          </Col>
        </Row>
      )}
    </Layout>
  );
}
