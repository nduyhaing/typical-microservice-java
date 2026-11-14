import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as cartService from '../modules/cart/services/CartService';
import * as orderService from '../modules/order/services/OrderService';
import CartPage from '../pages/cart';
import CheckoutPage from '../pages/checkout/[id]';
import MyOrdersPage from '../pages/my-orders';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
}));
vi.mock('../modules/cart/services/CartService');
vi.mock('../modules/order/services/OrderService');

const violet = { productId: 25, name: 'Galaxy S24 Violet', slug: 'galaxy-s24-violet', thumbnailUrl: '', price: 819, quantity: 2, available: true };
const checkout = {
  id: 'c-1',
  email: 'customer@shopapp.local',
  note: null,
  checkoutState: 'PENDING',
  totalAmount: 1638,
  checkoutItemVms: [{ id: 1, productId: 25, productName: 'Galaxy S24 Violet', quantity: 2, productPrice: 819 }],
};
const order = {
  id: 7,
  email: 'customer@shopapp.local',
  checkoutId: 'c-1',
  note: null,
  numberItem: 2,
  totalPrice: 1638,
  orderStatus: 'PENDING_PAYMENT',
  paymentStatus: 'PENDING',
  paymentMethod: 'COD',
  createdOn: '2026-11-14T12:00:00Z',
  orderItemVms: [{ productId: 25, productName: 'Galaxy S24 Violet', quantity: 2, productPrice: 819 }],
};

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: false, authenticatedUser: null });
});
afterEach(() => vi.resetAllMocks());

describe('Cart page checkout button', () => {
  it('creates a checkout from the cart and navigates to it', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue([violet]);
    vi.mocked(orderService.createCheckout).mockResolvedValue(checkout);

    render(<CartPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Checkout' }));

    expect(orderService.createCheckout).toHaveBeenCalledWith([{ productId: 25, quantity: 2 }]);
    expect(push).toHaveBeenCalledWith('/checkout/c-1');
  });

  it('is disabled while the cart has unavailable lines', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue([
      violet,
      { ...violet, productId: 9, name: 'Product no longer available', available: false },
    ]);

    render(<CartPage />);

    expect(await screen.findByRole('button', { name: 'Checkout' })).toBeDisabled();
  });
});

describe('Checkout page', () => {
  beforeEach(() => {
    router.query = { id: 'c-1' };
  });

  it('shows the items and the server total', async () => {
    vi.mocked(orderService.getCheckout).mockResolvedValue(checkout);

    render(<CheckoutPage />);

    expect(await screen.findByText('Galaxy S24 Violet')).toBeInTheDocument();
    expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,638.00');
    expect(screen.getByText('Cash on delivery')).toBeInTheDocument();
  });

  it('requires the address fields before placing the order', async () => {
    vi.mocked(orderService.getCheckout).mockResolvedValue(checkout);

    render(<CheckoutPage />);

    const place = await screen.findByRole('button', { name: 'Place order' });
    expect(place).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Full name'), 'Customer Dev');
    await userEvent.type(screen.getByLabelText('Phone'), '0123456789');
    await userEvent.type(screen.getByLabelText('Address'), '1 Main St');
    await userEvent.type(screen.getByLabelText('City'), 'Ho Chi Minh City');
    expect(place).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Country'), 'Vietnam');
    expect(place).toBeEnabled();
  });

  it('places the order and goes to my orders', async () => {
    vi.mocked(orderService.getCheckout).mockResolvedValue(checkout);
    vi.mocked(orderService.placeOrder).mockResolvedValue(order);

    render(<CheckoutPage />);
    await userEvent.type(await screen.findByLabelText('Full name'), 'Customer Dev');
    await userEvent.type(screen.getByLabelText('Phone'), '0123456789');
    await userEvent.type(screen.getByLabelText('Address'), '1 Main St');
    await userEvent.type(screen.getByLabelText('City'), 'Ho Chi Minh City');
    await userEvent.type(screen.getByLabelText('Country'), 'Vietnam');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));

    expect(orderService.placeOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        checkoutId: 'c-1',
        paymentMethod: 'COD',
        shippingAddressPostVm: expect.objectContaining({ contactName: 'Customer Dev', countryName: 'Vietnam' }),
      })
    );
    expect(push).toHaveBeenCalledWith('/my-orders?placed=7');
  });

  it('shows a login prompt on 401/403', async () => {
    vi.mocked(orderService.getCheckout).mockRejectedValue(new ApiError(401, {}));

    render(<CheckoutPage />);

    expect(await screen.findByRole('link', { name: 'Log in to check out' })).toBeInTheDocument();
  });
});

describe('My orders page', () => {
  it('lists the orders with status and total', async () => {
    vi.mocked(orderService.getMyOrders).mockResolvedValue([order]);

    render(<MyOrdersPage />);

    expect(await screen.findByText('Order #7')).toBeInTheDocument();
    expect(screen.getByText('Pending payment')).toBeInTheDocument();
    expect(screen.getByTestId('order-total-7')).toHaveTextContent('$1,638.00');
    expect(screen.getByText(/Galaxy S24 Violet/)).toBeInTheDocument();
  });

  it('confirms a just-placed order', async () => {
    router.query = { placed: '7' };
    vi.mocked(orderService.getMyOrders).mockResolvedValue([order]);

    render(<MyOrdersPage />);

    expect(await screen.findByText('Thank you! Your order #7 has been placed.')).toBeInTheDocument();
  });

  it('shows the empty state', async () => {
    vi.mocked(orderService.getMyOrders).mockResolvedValue([]);

    render(<MyOrdersPage />);

    expect(await screen.findByText('You have no orders yet.')).toBeInTheDocument();
  });

  it('shows a login prompt when anonymous', async () => {
    vi.mocked(orderService.getMyOrders).mockRejectedValue(new ApiError(403, {}));

    render(<MyOrdersPage />);

    expect(await screen.findByRole('link', { name: 'Log in to see your orders' })).toBeInTheDocument();
  });
});
