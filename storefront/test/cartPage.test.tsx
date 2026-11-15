import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as cartService from '../modules/cart/services/CartService';
import CartPage from '../pages/cart';

vi.mock('../modules/auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
}));
vi.mock('../modules/cart/services/CartService');
vi.mock('../modules/order/services/OrderService');
vi.mock('next/router', () => ({ useRouter: () => ({ push: vi.fn(), query: {}, isReady: true }) }));

const lines = [
  { productId: 1, name: 'Galaxy S24 Violet', slug: 'galaxy-s24-violet', thumbnailUrl: '', price: 819, quantity: 2, available: true },
  { productId: 2, name: 'USB-C Cable', slug: 'usb-c-cable', thumbnailUrl: '', price: 19, quantity: 1, available: true },
];

const unavailable = {
  productId: 9, name: 'Product no longer available', slug: '', thumbnailUrl: '', price: 0, quantity: 4, available: false,
};

describe('Cart page', () => {
  beforeEach(() => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: false, authenticatedUser: null });
  });
  afterEach(() => vi.resetAllMocks());

  it('shows items with line totals and the cart total', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue(lines);

    render(<CartPage />);

    expect(await screen.findByText('Galaxy S24 Violet')).toBeInTheDocument();
    expect(screen.getByTestId('line-total-1')).toHaveTextContent('$1,638.00');
    expect(screen.getByTestId('line-total-2')).toHaveTextContent('$19.00');
    expect(screen.getByTestId('cart-total')).toHaveTextContent('$1,657.00');
  });

  it('shows the empty state', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue([]);

    render(<CartPage />);

    expect(await screen.findByText('Your cart is empty.')).toBeInTheDocument();
  });

  it('shows a login prompt when anonymous', async () => {
    vi.mocked(cartService.getCartLines).mockRejectedValue(new ApiError(403, {}));

    render(<CartPage />);

    expect(await screen.findByRole('link', { name: 'Log in to see your cart' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/keycloak'
    );
  });

  it('plus updates the quantity', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue(lines);
    vi.mocked(cartService.updateQuantity).mockResolvedValue(undefined);

    render(<CartPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Increase Galaxy S24 Violet' }));

    expect(cartService.updateQuantity).toHaveBeenCalledWith(1, 3);
  });

  it('minus at quantity 1 removes the item', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue(lines);
    vi.mocked(cartService.removeItem).mockResolvedValue(undefined);

    render(<CartPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Decrease USB-C Cable' }));

    expect(cartService.removeItem).toHaveBeenCalledWith(2);
    expect(cartService.updateQuantity).not.toHaveBeenCalled();
  });

  it('shows an unavailable item with a Remove button and leaves it out of the total', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue([...lines, unavailable]);
    vi.mocked(cartService.removeItem).mockResolvedValue(undefined);

    render(<CartPage />);

    expect(await screen.findByText('No longer available')).toBeInTheDocument();
    expect(screen.getByTestId('cart-total')).toHaveTextContent('$1,657.00');
    expect(screen.queryByRole('button', { name: 'Increase Product no longer available' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove Product no longer available' }));
    expect(cartService.removeItem).toHaveBeenCalledWith(9);
  });
  it('shows the stock message when the cart refuses a quantity', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue(lines);
    vi.mocked(cartService.updateQuantity).mockRejectedValue(
      new ApiError(400, { detail: 'Only 2 of Galaxy S24 Violet are in stock' })
    );

    render(<CartPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Increase Galaxy S24 Violet' }));

    expect(await screen.findByText('Only 2 of Galaxy S24 Violet are in stock')).toBeInTheDocument();
  });

  it('caps plus at the tracked stock and flags lines above it', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue([
      { ...lines[0], quantity: 2, stockQuantity: 2 },
      { ...lines[1], quantity: 3, stockQuantity: 1 },
    ]);

    render(<CartPage />);

    expect(await screen.findByRole('button', { name: 'Increase Galaxy S24 Violet' })).toBeDisabled();
    expect(screen.getByTestId('stock-2')).toHaveTextContent('Only 1 in stock');
    expect(screen.queryByTestId('stock-1')).not.toBeInTheDocument();
  });
});

