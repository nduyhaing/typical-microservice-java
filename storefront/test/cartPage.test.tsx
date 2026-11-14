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

const lines = [
  { productId: 1, name: 'Galaxy S24 Violet', slug: 'galaxy-s24-violet', thumbnailUrl: '', price: 819, quantity: 2 },
  { productId: 2, name: 'USB-C Cable', slug: 'usb-c-cable', thumbnailUrl: '', price: 19, quantity: 1 },
];

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
});
