import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as customerService from '../modules/customer/services/CustomerService';
import ProfilePage from '../pages/profile';

vi.mock('../modules/auth/services/AuthenticationService');
vi.mock('../modules/customer/services/CustomerService');

describe('Profile page', () => {
  beforeEach(() => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: false, authenticatedUser: null });
  });
  afterEach(() => vi.resetAllMocks());

  it('renders the profile', async () => {
    vi.mocked(customerService.getMyProfile).mockResolvedValue({
      id: 'u-1',
      username: 'customer',
      email: 'customer@shopapp.local',
      firstName: 'Customer',
      lastName: 'Dev',
    });

    render(<ProfilePage />);

    expect(await screen.findByText('customer@shopapp.local')).toBeInTheDocument();
    expect(screen.getByText('Customer Dev')).toBeInTheDocument();
  });

  it('shows a login prompt on 401/403', async () => {
    vi.mocked(customerService.getMyProfile).mockRejectedValue(new ApiError(403, {}));

    render(<ProfilePage />);

    expect(await screen.findByRole('link', { name: 'Log in to see your profile' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/keycloak'
    );
  });
});
