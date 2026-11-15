import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import Pager from '../../../common/components/Pager';
import { useFetch } from '../../../common/hooks/useFetch';
import { formatPrice } from '../../../common/utils/format';
import {
  Payment,
  PAYMENT_METHOD_LABELS,
  PAYMENT_METHODS,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUSES,
  PaymentFilter,
  PaymentMethod,
  PaymentStatus,
} from '../../../modules/payment/models/Payment';
import { confirmCodPayment, getPayments } from '../../../modules/payment/services/PaymentService';

const NO_FILTER: PaymentFilter = { orderId: '', paymentStatus: '', paymentMethod: '' };

function statusBg(status: PaymentStatus): string {
  return status === 'COMPLETED' ? 'success' : status === 'CANCELLED' ? 'secondary' : 'warning';
}

/**
 * Payments (shop_app addition; YAS had no payments page): filter by order, status and method. A pending
 * cash-on-delivery payment is confirmed here when the cash was collected, which marks the order paid
 * (decisions.md 5-15, 5-19).
 */
export default function PaymentsPage() {
  const [pageNo, setPageNo] = useState(0);
  const [filter, setFilter] = useState<PaymentFilter>(NO_FILTER);
  const [draft, setDraft] = useState<PaymentFilter>(NO_FILTER);
  const { data, error, loading } = useFetch(() => getPayments(pageNo, filter), [pageNo, filter]);
  const [changed, setChanged] = useState<Record<number, Payment>>({});
  const [confirming, setConfirming] = useState<number>();
  const [confirmError, setConfirmError] = useState<unknown>();

  function search(event: FormEvent) {
    event.preventDefault();
    setPageNo(0);
    setFilter({ ...draft, orderId: draft.orderId.trim() });
  }

  async function confirmCash(payment: Payment) {
    if (!window.confirm(`Cash of ${formatPrice(payment.amount)} received for order #${payment.orderId}?`)) {
      return;
    }
    setConfirming(payment.id);
    setConfirmError(undefined);
    try {
      const confirmed = await confirmCodPayment(payment.id);
      setChanged((current) => ({ ...current, [payment.id]: confirmed }));
    } catch (e) {
      setConfirmError(e);
    } finally {
      setConfirming(undefined);
    }
  }

  return (
    <Layout title="Payments">
      <h1 className="h3 mb-3">Payments</h1>
      <Form onSubmit={search} className="mb-3">
        <Row className="g-2 align-items-end">
          <Form.Group as={Col} md={2} controlId="payment-filter-order">
            <Form.Label>Order</Form.Label>
            <Form.Control
              inputMode="numeric"
              value={draft.orderId}
              maxLength={18}
              onChange={(e) => setDraft({ ...draft, orderId: e.target.value.replace(/\D/g, '') })}
            />
          </Form.Group>
          <Form.Group as={Col} md={3} controlId="payment-filter-status">
            <Form.Label>Status</Form.Label>
            <Form.Select
              value={draft.paymentStatus}
              onChange={(e) => setDraft({ ...draft, paymentStatus: e.target.value as PaymentStatus | '' })}
            >
              <option value="">All</option>
              {PAYMENT_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {PAYMENT_STATUS_LABELS[status]}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
          <Form.Group as={Col} md={3} controlId="payment-filter-method">
            <Form.Label>Method</Form.Label>
            <Form.Select
              value={draft.paymentMethod}
              onChange={(e) => setDraft({ ...draft, paymentMethod: e.target.value as PaymentMethod | '' })}
            >
              <option value="">All</option>
              {PAYMENT_METHODS.map((method) => (
                <option key={method} value={method}>
                  {PAYMENT_METHOD_LABELS[method]}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
          <Col md={2}>
            <Button type="submit" variant="outline-primary">
              Search
            </Button>
          </Col>
        </Row>
      </Form>
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {confirmError !== undefined && <ErrorMessage error={confirmError} />}
      {data && data.payments.length === 0 && <p>No payments found.</p>}
      {data && data.payments.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Payment</th>
              <th>Order</th>
              <th>Created</th>
              <th className="text-end">Amount</th>
              <th>Method</th>
              <th>Status</th>
              <th>PayPal capture</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.payments
              .map((listed) => changed[listed.id] ?? listed)
              .map((payment) => (
                <tr key={payment.id}>
                  <td>{payment.id}</td>
                  <td>
                    <Link href={`/sales/orders/${payment.orderId}`}>#{payment.orderId}</Link>
                  </td>
                  <td className="small text-nowrap">{new Date(payment.createdOn).toLocaleString()}</td>
                  <td className="text-end">{formatPrice(payment.amount)}</td>
                  <td>{PAYMENT_METHOD_LABELS[payment.paymentMethod] ?? payment.paymentMethod}</td>
                  <td>
                    <Badge bg={statusBg(payment.paymentStatus)}>
                      {PAYMENT_STATUS_LABELS[payment.paymentStatus] ?? payment.paymentStatus}
                    </Badge>
                    {payment.paymentStatus === 'COMPLETED' && !payment.orderConfirmed && (
                      <div className="small text-danger">Order not updated yet</div>
                    )}
                    {payment.failureMessage && <div className="small text-muted">{payment.failureMessage}</div>}
                  </td>
                  <td className="small">{payment.gatewayTransactionId ?? '—'}</td>
                  <td className="text-end text-nowrap">
                    {payment.paymentMethod === 'COD' && payment.paymentStatus === 'PENDING' && (
                      <Button
                        size="sm"
                        variant="outline-success"
                        disabled={confirming === payment.id}
                        onClick={() => confirmCash(payment)}
                      >
                        Confirm cash received
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="payments" onChange={setPageNo} />}
    </Layout>
  );
}
