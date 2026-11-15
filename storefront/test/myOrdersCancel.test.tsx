import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import * as api from '../common/services/ApiClientService';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as orderService from '../modules/order/services/OrderService';
import MyOrdersPage from '../pages/my-orders';

vi.mock('next/router', () => ({ useRouter: () => ({ query: {}, push: vi.fn() }) }));
vi.mock('../modules/auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
}));

const order = (id: number, patch = {}) => ({
  id,
  email: 'customer@shopapp.local',
  checkoutId: `c-${id}`,
  note: null,
  numberItem: 1,
  totalPrice: 819,
  orderStatus: 'PENDING_PAYMENT',
  paymentStatus: 'PENDING',
  paymentMethod: 'COD',
  createdOn: '2026-11-14T12:00:00Z',
  orderItemVms: [{ productId: 25, productName: 'Galaxy S24 Violet', quantity: 1, productPrice: 819 }],
  cancellable: true,
  ...patch,
});

function card(id: number) {
  return screen.getByText(`Order #${id}`).closest('.card') as HTMLElement;
}

beforeEach(() => {
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: null });
});
afterEach(() => vi.restoreAllMocks());

describe('My orders: cancel', () => {
  it('cancelOrder posts to the cancel endpoint', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue(order(7, { orderStatus: 'CANCELLED', cancellable: false }));
    await orderService.cancelOrder(7);
    expect(post).toHaveBeenCalledWith('/order/storefront/orders/7/cancel', {});
  });

  it('offers "Cancel order" only on cancellable orders, and shows the status labels', async () => {
    vi.spyOn(orderService, 'getMyOrders').mockResolvedValue([
      order(7),
      order(8, { orderStatus: 'PAID', paymentStatus: 'COMPLETED', cancellable: false }),
      order(9, { orderStatus: 'CANCELLED', cancellable: false }),
    ]);

    render(<MyOrdersPage />);

    await screen.findByText('Order #7');
    expect(within(card(7)).getByRole('button', { name: 'Cancel order' })).toBeInTheDocument();
    expect(within(card(8)).queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
    expect(within(card(8)).getByText('Paid')).toBeInTheDocument();
    expect(within(card(9)).getByText('Cancelled')).toBeInTheDocument();
    expect(within(card(9)).queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('asks for confirmation, then cancels and shows the cancelled order', async () => {
    vi.spyOn(orderService, 'getMyOrders').mockResolvedValue([order(7)]);
    const cancel = vi.spyOn(orderService, 'cancelOrder').mockResolvedValue(order(7, { orderStatus: 'CANCELLED', cancellable: false }));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<MyOrdersPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    expect(confirm).toHaveBeenCalledWith('Cancel order #7? This cannot be undone.');
    expect(cancel).not.toHaveBeenCalled();

    confirm.mockReturnValue(true);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel order' }));

    expect(cancel).toHaveBeenCalledWith(7);
    expect(await screen.findByText('Order #7 has been cancelled.')).toBeInTheDocument();
    expect(within(card(7)).getByText('Cancelled')).toBeInTheDocument();
    expect(within(card(7)).queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it("shows the server's reason when the order can no longer be cancelled", async () => {
    vi.spyOn(orderService, 'getMyOrders').mockResolvedValue([order(7)]);
    vi.spyOn(orderService, 'cancelOrder').mockRejectedValue(
      new ApiError(400, { detail: 'Order 7 can no longer be cancelled (status PAID)' })
    );
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<MyOrdersPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Order 7 can no longer be cancelled (status PAID)');
  });

  it('any other failure: a generic message', async () => {
    vi.spyOn(orderService, 'getMyOrders').mockResolvedValue([order(7)]);
    vi.spyOn(orderService, 'cancelOrder').mockRejectedValue(new ApiError(503, null));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<MyOrdersPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The order could not be cancelled right now. Please try again.');
  });
});
