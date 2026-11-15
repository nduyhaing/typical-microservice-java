import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as ratingService from '../modules/rating/services/RatingService';
import HomePage from '../pages/index';

vi.mock('next/router', () => ({ useRouter: () => ({ push: vi.fn(), query: {}, isReady: true, pathname: '/' }) }));
vi.mock('../modules/auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
}));

vi.mock('../modules/rating/services/RatingService');

beforeEach(() => vi.mocked(ratingService.getLatestRatings).mockResolvedValue([]));
afterEach(() => vi.resetAllMocks());

describe('Back office layout and dashboard', () => {
  it('shows the navigation grouped like YAS, the admin user and a logout form', async () => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({
      isAuthenticated: true,
      authenticatedUser: { username: 'admin' },
    });

    render(<HomePage />);

    expect(await screen.findByText('admin')).toBeInTheDocument();
    const nav = screen.getByRole('navigation');
    const groups: [string, [string, string][]][] = [
      ['Catalog', [['Brands', '/catalog/brands'], ['Categories', '/catalog/categories'], ['Products', '/catalog/products'], ['Reviews', '/reviews']]],
      ['Location', [['Countries', '/location/countries'], ['States or provinces', '/location/state-or-provinces']]],
      ['Tax', [['Tax classes', '/tax/tax-classes'], ['Tax rates', '/tax/tax-rates']]],
      [
        'Inventory',
        [
          ['Warehouses', '/inventory/warehouses'],
          ['Warehouse stocks', '/inventory/warehouse-stocks'],
          ['Warehouse products', '/inventory/warehouse-products'],
        ],
      ],
      ['Promotion', [['Promotions', '/promotion/manager-promotion']]],
    ];
    for (const [group, links] of groups) {
      // the dashboard lists every page under its group heading
      const section = screen.getByRole('region', { name: group });
      for (const [name, href] of links) {
        expect(within(section).getByRole('link', { name })).toHaveAttribute('href', href);
      }
      // the navigation opens a dropdown per group
      await userEvent.click(within(nav).getByRole('button', { name: group }));
      for (const [name, href] of links) {
        expect(within(nav).getByRole('link', { name })).toHaveAttribute('href', href);
      }
    }
    // the dashboard also shows the latest ratings
    expect(screen.getByRole('heading', { name: 'Latest ratings' })).toBeInTheDocument();
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
