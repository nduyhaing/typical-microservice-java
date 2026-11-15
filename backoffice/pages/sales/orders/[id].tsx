import Link from 'next/link';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useFetch } from '../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../common/hooks/useRecordId';
import { formatPrice } from '../../../common/utils/format';
import {
  addressLines,
  CONFIRMED_STATUSES,
  DELIVERY_METHOD_LABELS,
  DELIVERY_METHODS,
  DELIVERY_STATUS_LABELS,
  DeliveryMethod,
  GoodsBack,
  OrderAddress,
  OrderDetail,
  OrderStatus,
  OrderStatusChange,
  ORDER_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  STATUS_ACTIONS,
} from '../../../modules/order/models/Order';
import { getOrder, updateOrderStatus } from '../../../modules/order/services/OrderService';

const CONFIRM_TEXT: Partial<Record<OrderStatus, (id: number) => string>> = {
  CANCELLED: (id) => `Cancel order #${id}? Its stock and coupon use are given back.`,
  REJECT: (id) => `Reject order #${id}? Its stock and coupon use are given back.`,
  REFUND: (id) => `Refund order #${id}? Record this only after the money was returned.`,
};

function AddressCard({ title, address }: { title: string; address: OrderAddress | null }) {
  const id = `address-${title.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <Card as="section" aria-labelledby={id} className="h-100">
      <Card.Body>
        <Card.Title as="h2" id={id} className="h6">
          {title}
        </Card.Title>
        {addressLines(address).map((line) => (
          <div key={line}>{line}</div>
        ))}
        {!address && <div className="text-muted">—</div>}
      </Card.Body>
    </Card>
  );
}

function GoodsBackInfo({ goodsBack }: { goodsBack: GoodsBack }) {
  const state = (toGive: boolean, done: string | null, what: string, doneText: string) => {
    if (!toGive) {
      return null;
    }
    return <li>{done ? doneText : `${what}: pending (retried automatically)`}</li>;
  };
  return (
    <ul className="mb-0">
      {state(goodsBack.stockToRestore, goodsBack.stockRestoredOn, 'Stock', 'Stock given back')}
      {state(goodsBack.couponToRelease, goodsBack.couponReleasedOn, 'Coupon use', 'Coupon use given back')}
      {!goodsBack.stockToRestore && !goodsBack.couponToRelease && <li>Nothing to give back</li>}
    </ul>
  );
}

/** One order (YAS backoffice order detail): items, totals, addresses, and the status changes it allows. */
export default function OrderDetailPage() {
  const id = useRecordId();
  const { data, error, loading } = useFetch(() => (id ? getOrder(id) : pending<OrderDetail>()), [id]);
  const [updated, setUpdated] = useState<OrderDetail>();
  const order = updated ?? data;
  const [actionError, setActionError] = useState<unknown>();
  const [notice, setNotice] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [deliveryChoice, setDeliveryMethod] = useState<DeliveryMethod | ''>();
  const deliveryMethod = deliveryChoice ?? order?.deliveryMethod ?? '';

  async function change(request: OrderStatusChange, success: string) {
    setBusy(true);
    setActionError(undefined);
    setNotice(undefined);
    try {
      const next = await updateOrderStatus(order!.id, request);
      setUpdated(next);
      setDeliveryMethod(undefined);
      setNotice(success);
    } catch (e) {
      setActionError(e);
    } finally {
      setBusy(false);
    }
  }

  function moveTo(status: OrderStatus) {
    const confirmText = CONFIRM_TEXT[status];
    if (confirmText && !window.confirm(confirmText(order!.id))) {
      return;
    }
    const request: OrderStatusChange = { orderStatus: status };
    if (status === 'REJECT' && rejectReason.trim()) {
      request.rejectReason = rejectReason.trim();
    }
    change(request, `Order moved to ${ORDER_STATUS_LABELS[status]}.`);
  }

  function saveDelivery() {
    if (!deliveryMethod) {
      return;
    }
    change({ orderStatus: order!.orderStatus, deliveryMethod }, 'Delivery saved.');
  }

  const discount = order?.discount ?? 0;
  const tax = order?.tax ?? 0;
  const ended = order ? order.allowedStatuses.length === 0 : false;

  return (
    <Layout title={order ? `Order #${order.id}` : 'Order'}>
      <p>
        <Link href="/sales/orders">← Orders</Link>
      </p>
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {order && (
        <>
          <h1 className="h3 mb-3">Order #{order.id}</h1>
          <Row className="g-3 mb-3">
            <Col md={4}>
              <dl className="mb-0">
                <dt>Status</dt>
                <dd>
                  <Badge bg="secondary">{ORDER_STATUS_LABELS[order.orderStatus]}</Badge>
                </dd>
                <dt>Payment</dt>
                <dd>
                  {order.paymentStatus ? PAYMENT_STATUS_LABELS[order.paymentStatus] : '—'}
                  {order.paymentMethod && ` (${order.paymentMethod})`}
                  {order.paymentId !== null && ` · payment ${order.paymentId}`}
                </dd>
                <dt>Delivery</dt>
                <dd>
                  {order.deliveryStatus ? DELIVERY_STATUS_LABELS[order.deliveryStatus] : '—'}
                  {order.deliveryMethod && ` · ${DELIVERY_METHOD_LABELS[order.deliveryMethod]}`}
                </dd>
              </dl>
            </Col>
            <Col md={4}>
              <dl className="mb-0">
                <dt>Customer</dt>
                <dd>{order.email}</dd>
                <dt>Placed</dt>
                <dd>{new Date(order.createdOn).toLocaleString()}</dd>
                {order.lastModifiedOn && (
                  <>
                    <dt>Last changed</dt>
                    <dd>
                      {new Date(order.lastModifiedOn).toLocaleString()}
                      {order.lastModifiedBy && ` by ${order.lastModifiedBy}`}
                    </dd>
                  </>
                )}
              </dl>
            </Col>
            <Col md={4}>
              <dl className="mb-0">
                {order.note && (
                  <>
                    <dt>Note</dt>
                    <dd className="text-break">{order.note}</dd>
                  </>
                )}
                {order.rejectReason && (
                  <>
                    <dt>Reject reason</dt>
                    <dd className="text-break">{order.rejectReason}</dd>
                  </>
                )}
                {order.goodsBack && (
                  <>
                    <dt>Stock and coupon</dt>
                    <dd>
                      <GoodsBackInfo goodsBack={order.goodsBack} />
                    </dd>
                  </>
                )}
              </dl>
            </Col>
          </Row>

          {notice && <Alert variant="success">{notice}</Alert>}
          {actionError !== undefined && <ErrorMessage error={actionError} />}

          <Card className="mb-3">
            <Card.Body>
              <Card.Title as="h2" className="h6">
                Change status
              </Card.Title>
              {ended ? (
                <p className="mb-0">No further status changes.</p>
              ) : (
                <>
                  <ButtonGroup aria-label="Status actions" className="flex-wrap gap-2 mb-2">
                    {order.allowedStatuses.map((status) => (
                      <Button
                        key={status}
                        variant={CONFIRMED_STATUSES.includes(status) ? 'outline-danger' : 'outline-primary'}
                        disabled={busy}
                        onClick={() => moveTo(status)}
                      >
                        {STATUS_ACTIONS[status]}
                      </Button>
                    ))}
                  </ButtonGroup>
                  <Row className="g-2 align-items-end">
                    {order.allowedStatuses.includes('REJECT') && (
                      <Form.Group as={Col} md={5} controlId="order-reject-reason">
                        <Form.Label>Reject reason</Form.Label>
                        <Form.Control value={rejectReason} maxLength={255} onChange={(e) => setRejectReason(e.target.value)} />
                      </Form.Group>
                    )}
                    <Form.Group as={Col} md={4} controlId="order-delivery-method">
                      <Form.Label>Delivery method</Form.Label>
                      <Form.Select value={deliveryMethod} onChange={(e) => setDeliveryMethod(e.target.value as DeliveryMethod | '')}>
                        <option value="">—</option>
                        {DELIVERY_METHODS.map((method) => (
                          <option key={method} value={method}>
                            {DELIVERY_METHOD_LABELS[method]}
                          </option>
                        ))}
                      </Form.Select>
                    </Form.Group>
                    <Col md={3}>
                      <Button variant="outline-secondary" disabled={busy || !deliveryMethod} onClick={saveDelivery}>
                        Save delivery
                      </Button>
                    </Col>
                  </Row>
                </>
              )}
            </Card.Body>
          </Card>

          <Table responsive aria-label="Items">
            <thead>
              <tr>
                <th>Product</th>
                <th className="text-end">Price</th>
                <th className="text-end">Quantity</th>
                <th className="text-end">Discount</th>
                <th className="text-end">Tax</th>
                <th className="text-end">Amount</th>
              </tr>
            </thead>
            <tbody>
              {order.orderItemVms.map((item) => (
                <tr key={item.productId}>
                  <td>{item.productName}</td>
                  <td className="text-end">{formatPrice(item.productPrice)}</td>
                  <td className="text-end">{item.quantity}</td>
                  <td className="text-end">{item.discountAmount ? `−${formatPrice(item.discountAmount)}` : '—'}</td>
                  <td className="text-end">
                    {item.taxAmount ? `${formatPrice(item.taxAmount)} (${item.taxPercent ?? 0} %)` : '—'}
                  </td>
                  <td className="text-end">{formatPrice(item.productPrice * item.quantity)}</td>
                </tr>
              ))}
            </tbody>
          </Table>

          <Row className="g-3">
            <Col md={4}>
              <AddressCard title="Shipping address" address={order.shippingAddressVm} />
            </Col>
            <Col md={4}>
              <AddressCard title="Billing address" address={order.billingAddressVm} />
            </Col>
            <Col md={4}>
              <Card as="section" aria-labelledby="order-totals" className="h-100">
                <Card.Body>
                  <Card.Title as="h2" id="order-totals" className="h6">
                    Totals
                  </Card.Title>
                  <dl className="row mb-0">
                    <dt className="col-7 fw-normal">Subtotal</dt>
                    <dd className="col-5 text-end mb-1">{formatPrice(order.subtotal)}</dd>
                    {discount > 0 && (
                      <>
                        <dt className="col-7 fw-normal">{order.couponCode ? `Discount (${order.couponCode})` : 'Discount'}</dt>
                        <dd className="col-5 text-end mb-1">−{formatPrice(discount)}</dd>
                      </>
                    )}
                    <dt className="col-7 fw-normal">Tax</dt>
                    <dd className="col-5 text-end mb-1">{formatPrice(tax)}</dd>
                    {!!order.deliveryFee && (
                      <>
                        <dt className="col-7 fw-normal">Delivery</dt>
                        <dd className="col-5 text-end mb-1">{formatPrice(order.deliveryFee)}</dd>
                      </>
                    )}
                    <dt className="col-7">Total</dt>
                    <dd className="col-5 text-end mb-0 fw-bold">{formatPrice(order.totalPrice)}</dd>
                  </dl>
                </Card.Body>
              </Card>
            </Col>
          </Row>
        </>
      )}
    </Layout>
  );
}
