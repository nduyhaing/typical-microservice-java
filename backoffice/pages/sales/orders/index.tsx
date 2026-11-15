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
  DELIVERY_STATUS_LABELS,
  ORDER_STATUS_LABELS,
  ORDER_STATUSES,
  OrderFilter,
  OrderStatus,
  PAYMENT_STATUS_LABELS,
} from '../../../modules/order/models/Order';
import { getOrders } from '../../../modules/order/services/OrderService';

const NO_FILTER: OrderFilter = { email: '', productName: '', orderStatus: [], createdFrom: '', createdTo: '' };

/** Orders (YAS backoffice pages/sales/orders): filter by customer email, product, status and dates; open one. */
export default function OrdersPage() {
  const [pageNo, setPageNo] = useState(0);
  const [filter, setFilter] = useState<OrderFilter>(NO_FILTER);
  const [draft, setDraft] = useState<OrderFilter>(NO_FILTER);
  const { data, error, loading } = useFetch(() => getOrders(pageNo, filter), [pageNo, filter]);

  function search(event: FormEvent) {
    event.preventDefault();
    setPageNo(0);
    setFilter({ ...draft, email: draft.email.trim(), productName: draft.productName.trim() });
  }

  return (
    <Layout title="Orders">
      <h1 className="h3 mb-3">Orders</h1>
      <Form onSubmit={search} className="mb-3">
        <Row className="g-2 align-items-end">
          <Form.Group as={Col} md={3} controlId="order-filter-email">
            <Form.Label>Customer email</Form.Label>
            <Form.Control value={draft.email} maxLength={255} onChange={(e) => setDraft({ ...draft, email: e.target.value })} />
          </Form.Group>
          <Form.Group as={Col} md={3} controlId="order-filter-product">
            <Form.Label>Product name</Form.Label>
            <Form.Control
              value={draft.productName}
              maxLength={255}
              onChange={(e) => setDraft({ ...draft, productName: e.target.value })}
            />
          </Form.Group>
          <Form.Group as={Col} md={3} controlId="order-filter-status">
            <Form.Label>Status</Form.Label>
            <Form.Select
              multiple
              htmlSize={3}
              value={draft.orderStatus}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  orderStatus: Array.from(e.target.selectedOptions, (option) => option.value as OrderStatus),
                })
              }
            >
              {ORDER_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {ORDER_STATUS_LABELS[status]}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
        </Row>
        <Row className="g-2 align-items-end mt-1">
          <Form.Group as={Col} md={3} controlId="order-filter-from">
            <Form.Label>Created from</Form.Label>
            <Form.Control type="date" value={draft.createdFrom} onChange={(e) => setDraft({ ...draft, createdFrom: e.target.value })} />
          </Form.Group>
          <Form.Group as={Col} md={3} controlId="order-filter-to">
            <Form.Label>Created to</Form.Label>
            <Form.Control type="date" value={draft.createdTo} onChange={(e) => setDraft({ ...draft, createdTo: e.target.value })} />
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
      {data && data.orderList.length === 0 && <p>No orders found.</p>}
      {data && data.orderList.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Order</th>
              <th>Customer</th>
              <th>Created</th>
              <th className="text-end">Total</th>
              <th>Status</th>
              <th>Payment</th>
              <th>Delivery</th>
            </tr>
          </thead>
          <tbody>
            {data.orderList.map((order) => (
              <tr key={order.id}>
                <td>
                  <Link href={`/sales/orders/${order.id}`}>#{order.id}</Link>
                </td>
                <td>
                  <div>{order.email}</div>
                  {order.billingAddressVm?.contactName && <div className="small text-muted">{order.billingAddressVm.contactName}</div>}
                </td>
                <td className="small text-nowrap">{new Date(order.createdOn).toLocaleString()}</td>
                <td className="text-end">{formatPrice(order.totalPrice)}</td>
                <td>
                  <Badge bg="secondary">{ORDER_STATUS_LABELS[order.orderStatus] ?? order.orderStatus}</Badge>
                </td>
                <td>{order.paymentStatus ? PAYMENT_STATUS_LABELS[order.paymentStatus] : '—'}</td>
                <td>{order.deliveryStatus ? DELIVERY_STATUS_LABELS[order.deliveryStatus] : '—'}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="orders" onChange={setPageNo} />}
    </Layout>
  );
}
