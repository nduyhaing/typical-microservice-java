import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import * as authService from '../modules/auth/services/AuthenticationService';
import HomePage from '../pages/index';

vi.mock('next/router', () => ({ useRouter: () => ({ push: vi.fn(), query: {}, isReady: true, pathname: '/' }) }));
vi.mock('../modules/auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
}));

afterEach(() => vi.resetAllMocks());

describe('Back office layout and dashboard', () => {
  it('shows the catalog navigation, the admin user and a logout form', async () => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({
      isAuthenticated: true,
      authenticatedUser: { username: 'admin' },
    });

    render(<HomePage />);

    expect(await screen.findByText('admin')).toBeInTheDocument();
    const nav = screen.getByRole('navigation');
    expect(nav).toContainElement(screen.getAllByRole('link', { name: 'Brands' })[0]);
    for (const [name, href] of [
      ['Brands', '/catalog/brands'],
      ['Categories', '/catalog/categories'],
      ['Products', '/catalog/products'],
    ]) {
      const links = screen.getAllByRole('link', { name });
      // one in the navigation, one on the dashboard
      expect(links).toHaveLength(2);
      links.forEach((link) => expect(link).toHaveAttribute('href', href));
    }
    const logout = screen.getByRole('button', { name: 'Log out' }).closest('form')!;
    expect(logout).toHaveAttribute('action', '/logout');
    expect(logout).toHaveAttribute('method', 'post');
  });

  it('has no user name while the session is unknown', async () => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: false, authenticatedUser: null });

    render(<HomePage />);

    expect(await screen.findByRole('heading', { name: 'Back office' })).toBeInTheDocument();
    expect(screen.queryByText('admin')).not.toBeInTheDocument();
  });
});
