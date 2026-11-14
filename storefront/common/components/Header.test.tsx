import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import * as authService from '../../modules/auth/services/AuthenticationService';
import * as cartService from '../../modules/cart/services/CartService';
import Layout from './Layout';

vi.mock('../../modules/auth/services/AuthenticationService');
vi.mock('../../modules/cart/services/CartService');

describe('Header', () => {
  afterEach(() => vi.resetAllMocks());

  it('shows Log in when anonymous', async () => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: false, authenticatedUser: null });

    render(<Layout>page</Layout>);

    const login = await screen.findByRole('link', { name: 'Log in' });
    expect(login).toHaveAttribute('href', '/oauth2/authorization/keycloak');
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument();
  });

  it('shows username, profile, cart and Log out when logged in', async () => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({
      isAuthenticated: true,
      authenticatedUser: { username: 'customer' },
    });

    vi.mocked(cartService.getCartItemCount).mockResolvedValue(3);

    render(<Layout>page</Layout>);

    expect(await screen.findByText('customer')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Profile' })).toHaveAttribute('href', '/profile');
    expect(await screen.findByRole('link', { name: 'Cart (3)' })).toHaveAttribute('href', '/cart');
    const logout = screen.getByRole('button', { name: 'Log out' });
    expect(logout.closest('form')).toHaveAttribute('action', '/logout');
    expect(logout.closest('form')).toHaveAttribute('method', 'post');
    expect(screen.queryByRole('link', { name: 'Log in' })).not.toBeInTheDocument();
  });
});
