import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import { OrderBrief, OrderDetail } from '../modules/order/models/Order';
import * as orderService from '../modules/order/services/OrderService';
import OrderDetailPage from '../pages/sales/orders/[id]';
import OrdersPage from '../pages/sales/orders/index';

vi.mock('next/router', () => ({ useRouter: () => ({ push: vi.fn(), query: { id: '7' }, isReady: true }) }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/order/services/OrderService');

const brief = (id: number, patch: Partial<OrderBrief> = {}): OrderBrief => ({
  id,
  email: `c${id}@shop.local`,
  billingAddressVm: { contactName: `Customer ${id}`, phone: '0901', addressLine1: '1 Le Loi', addressLine2: null, city: 'HCMC', zipCode: null, districtName: null, stateOrProvinceName: null, countryName: 'Viet Nam' },
  totalPrice: 819,
  orderStatus: 'PENDING_PAYMENT',
  deliveryMethod: null,
  deliveryStatus: 'PREPARING',
  paymentStatus: 'PENDING',
  createdOn: '2026-11-10T12:00:00Z',
  ...patch,
});

const page = (list: OrderBrief[], patch = {}) => ({
  orderList: list,
  pageNo: 0,
  pageSize: 10,
  totalElements: list.length,
  totalPages: 1,
  isLast: true,
  ...patch,
});

const detail = (patch: Partial<OrderDetail> = {}): OrderDetail => ({
  id: 7,
  email: 'lan@shop.local',
  customerId: 'u-1',
  checkoutId: 'c-1',
  note: 'Ring twice',
  numberItem: 3,
  subtotal: 1648,
  tax: 81.9,
  discount: 81.9,
  totalPrice: 1648,
  deliveryFee: 0,
  couponCode: 'SAVE10',
  orderStatus: 'PENDING_PAYMENT',
  paymentStatus: 'PENDING',
  paymentMethod: 'COD',
  paymentId: null,
  deliveryStatus: 'PREPARING',
  deliveryMethod: null,
  rejectReason: null,
  createdOn: '2026-11-10T12:00:00Z',
  lastModifiedOn: '2026-11-10T12:00:00Z',
  lastModifiedBy: 'u-1',
  shippingAddressVm: { contactName: 'Lan Nguyen', phone: '0901', addressLine1: '1 Le Loi', addressLine2: null, city: 'HCMC', zipCode: '700000', districtName: 'District 1', stateOrProvinceName: 'Ho Chi Minh', countryName: 'Viet Nam' },
  billingAddressVm: { contactName: 'Lan Billing', phone: '0902', addressLine1: '2 Hai Ba Trung', addressLine2: null, city: 'HCMC', zipCode: null, districtName: null, stateOrProvinceName: null, countryName: 'Viet Nam' },
  orderItemVms: [
    { productId: 25, productName: 'Galaxy S24', quantity: 2, productPrice: 819, discountAmount: 81.9, taxPercent: 10, taxAmount: 81.9 },
    { productId: 27, productName: 'Phone Case', quantity: 1, productPrice: 10, discountAmount: 0, taxPercent: 0, taxAmount: 0 },
  ],
  allowedStatuses: ['ACCEPTED', 'PAID', 'CANCELLED', 'REJECT'],
  goodsBack: null,
  ...patch,
});

beforeEach(() => {
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
});
afterEach(() => vi.restoreAllMocks());

describe('Orders page', () => {
  it('lists orders with customer, total, status labels and a link to the detail', async () => {
    vi.mocked(orderService.getOrders).mockResolvedValue(
      page([brief(1), brief(2, { orderStatus: 'SHIPPING', paymentStatus: 'COMPLETED', deliveryStatus: 'DELIVERING' })])
    );

    render(<OrdersPage />);

    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(within(rows[1]).getByRole('link', { name: '#1' })).toHaveAttribute('href', '/sales/orders/1');
    expect(within(rows[1]).getByText('c1@shop.local')).toBeInTheDocument();
    expect(within(rows[1]).getByText('$819.00')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Pending payment')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Shipping')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Delivering')).toBeInTheDocument();
    expect(orderService.getOrders).toHaveBeenCalledWith(0, { email: '', productName: '', orderStatus: [], createdFrom: '', createdTo: '' });
  });

  it('searches with the filters from the first page', async () => {
    vi.mocked(orderService.getOrders).mockResolvedValue(page([brief(1)]));
    render(<OrdersPage />);
    await screen.findByText('c1@shop.local');

    await userEvent.type(screen.getByLabelText('Customer email'), ' lan@ ');
    await userEvent.type(screen.getByLabelText('Product name'), 'Galaxy');
    await userEvent.selectOptions(screen.getByLabelText('Status'), ['PAID', 'SHIPPING']);
    await userEvent.type(screen.getByLabelText('Created from'), '2026-11-01');
    await userEvent.type(screen.getByLabelText('Created to'), '2026-11-09');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() =>
      expect(orderService.getOrders).toHaveBeenLastCalledWith(0, {
        email: 'lan@',
        productName: 'Galaxy',
        orderStatus: ['PAID', 'SHIPPING'],
        createdFrom: '2026-11-01',
        createdTo: '2026-11-09',
      })
    );
  });

  it('pages through the results', async () => {
    vi.mocked(orderService.getOrders).mockResolvedValue(page([brief(1)], { totalElements: 11, totalPages: 2, isLast: false }));
    render(<OrdersPage />);
    await screen.findByText('c1@shop.local');

    await userEvent.click(screen.getByRole('button', { name: /next/i }));

    await waitFor(() => expect(orderService.getOrders).toHaveBeenLastCalledWith(1, expect.anything()));
  });

  it('says when nothing matches and shows errors', async () => {
    vi.mocked(orderService.getOrders).mockResolvedValueOnce(page([]));
    render(<OrdersPage />);
    expect(await screen.findByText('No orders found.')).toBeInTheDocument();
  });

  it('shows the server error', async () => {
    vi.mocked(orderService.getOrders).mockRejectedValue(new ApiError(400, { detail: 'size must be between 0 and 255', fieldErrors: [] }));
    render(<OrdersPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('size must be between 0 and 255');
  });
});

describe('Order detail page', () => {
  it('shows items, totals with tax and discount, both addresses and the statuses', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(detail());

    render(<OrderDetailPage />);

    expect(await screen.findByRole('heading', { name: 'Order #7' })).toBeInTheDocument();
    expect(orderService.getOrder).toHaveBeenCalledWith(7);
    const items = screen.getByRole('table', { name: 'Items' });
    expect(within(items).getAllByRole('row')).toHaveLength(3);
    expect(within(items).getByText('Galaxy S24')).toBeInTheDocument();
    const totals = screen.getByRole('region', { name: 'Totals' });
    expect(within(totals).getByText('Subtotal')).toBeInTheDocument();
    expect(within(totals).getByText('Discount (SAVE10)')).toBeInTheDocument();
    expect(within(totals).getByText('−$81.90')).toBeInTheDocument();
    expect(within(totals).getByText('Tax')).toBeInTheDocument();
    expect(within(totals).getAllByText('$1,648.00')).toHaveLength(2);
    expect(screen.getByRole('region', { name: 'Shipping address' })).toHaveTextContent('Lan Nguyen');
    expect(screen.getByRole('region', { name: 'Billing address' })).toHaveTextContent('Lan Billing');
    expect(screen.getByText('Pending payment')).toBeInTheDocument();
    expect(screen.getByText('Ring twice')).toBeInTheDocument();
  });

  it('offers only the allowed status changes', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(detail({ orderStatus: 'PAID', allowedStatuses: ['SHIPPING', 'REFUND'] }));

    render(<OrderDetailPage />);

    const actions = await screen.findByRole('group', { name: 'Status actions' });
    expect(within(actions).getAllByRole('button').map((button) => button.textContent)).toEqual(['Ship', 'Refund']);
  });

  it('a final order has no actions', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(detail({ orderStatus: 'COMPLETED', allowedStatuses: [] }));
    render(<OrderDetailPage />);
    expect(await screen.findByText('No further status changes.')).toBeInTheDocument();
  });

  it('moves the order and shows the answer', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(detail());
    vi.mocked(orderService.updateOrderStatus).mockResolvedValue(detail({ orderStatus: 'PAID', paymentStatus: 'COMPLETED', allowedStatuses: ['SHIPPING', 'REFUND'] }));
    render(<OrderDetailPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Mark as paid' }));

    expect(orderService.updateOrderStatus).toHaveBeenCalledWith(7, { orderStatus: 'PAID' });
    expect(await screen.findByText('Order moved to Paid.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ship' })).toBeInTheDocument();
  });

  it('cancel and refund ask for confirmation first', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(detail());
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<OrderDetailPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));

    expect(confirm).toHaveBeenCalledWith('Cancel order #7? Its stock and coupon use are given back.');
    expect(orderService.updateOrderStatus).not.toHaveBeenCalled();

    confirm.mockReturnValue(true);
    vi.mocked(orderService.updateOrderStatus).mockResolvedValue(
      detail({ orderStatus: 'CANCELLED', allowedStatuses: [], goodsBack: { requestedOn: '2026-11-10T13:00:00Z', stockToRestore: true, stockRestoredOn: '2026-11-10T13:00:01Z', couponToRelease: true, couponReleasedOn: null } })
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancel order' }));

    expect(orderService.updateOrderStatus).toHaveBeenCalledWith(7, { orderStatus: 'CANCELLED' });
    expect(await screen.findByText('Stock given back')).toBeInTheDocument();
    expect(screen.getByText('Coupon use: pending (retried automatically)')).toBeInTheDocument();
  });

  it('reject sends the reason', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(detail());
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(orderService.updateOrderStatus).mockResolvedValue(detail({ orderStatus: 'REJECT', allowedStatuses: [], rejectReason: 'Fake address' }));
    render(<OrderDetailPage />);

    await userEvent.type(await screen.findByLabelText('Reject reason'), ' Fake address ');
    await userEvent.click(screen.getByRole('button', { name: 'Reject' }));

    expect(orderService.updateOrderStatus).toHaveBeenCalledWith(7, { orderStatus: 'REJECT', rejectReason: 'Fake address' });
  });

  it('saves the delivery method without changing the status', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(detail());
    vi.mocked(orderService.updateOrderStatus).mockResolvedValue(detail({ deliveryMethod: 'GRAB_EXPRESS' }));
    render(<OrderDetailPage />);

    await userEvent.selectOptions(await screen.findByLabelText('Delivery method'), 'GRAB_EXPRESS');
    await userEvent.click(screen.getByRole('button', { name: 'Save delivery' }));

    expect(orderService.updateOrderStatus).toHaveBeenCalledWith(7, { orderStatus: 'PENDING_PAYMENT', deliveryMethod: 'GRAB_EXPRESS' });
  });

  it('shows a refused change', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(detail());
    vi.mocked(orderService.updateOrderStatus).mockRejectedValue(
      new ApiError(400, { detail: 'Order 7 cannot go from PAID to ACCEPTED', fieldErrors: [] })
    );
    render(<OrderDetailPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Accept' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Order 7 cannot go from PAID to ACCEPTED');
  });

  it('unknown order', async () => {
    vi.mocked(orderService.getOrder).mockRejectedValue(new ApiError(404, { detail: 'Order 7 is not found', fieldErrors: [] }));
    render(<OrderDetailPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Order 7 is not found');
  });
});
