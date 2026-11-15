import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as addressService from '../modules/address/services/AddressService';
import * as authService from '../modules/auth/services/AuthenticationService';
import AddressPage from '../pages/address';

vi.mock('../modules/auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
}));
vi.mock('../modules/cart/services/CartService');
vi.mock('../modules/address/services/AddressService');

const home = {
  id: 5,
  contactName: 'Home',
  phone: '0123456789',
  addressLine1: '1 Main St',
  addressLine2: null,
  city: 'Ho Chi Minh City',
  zipCode: '700000',
  districtId: 100,
  districtName: 'District 1',
  stateOrProvinceId: 10,
  stateOrProvinceName: 'Ho Chi Minh',
  countryId: 1,
  countryName: 'Viet Nam',
  isActive: true,
};
const office = {
  ...home,
  id: 6,
  contactName: 'Office',
  addressLine1: '2 Side St',
  districtId: null,
  districtName: null,
  stateOrProvinceId: null,
  stateOrProvinceName: null,
  zipCode: null,
  isActive: false,
};

describe('Address book page', () => {
  beforeEach(() => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: false, authenticatedUser: null });
    vi.mocked(addressService.getCountries).mockResolvedValue([{ id: 1, code2: 'VN', name: 'Viet Nam' }]);
    vi.mocked(addressService.getStatesOrProvinces).mockResolvedValue([]);
    vi.mocked(addressService.getDistricts).mockResolvedValue([]);
  });
  afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
  });

  it('lists the addresses with a badge on the default one', async () => {
    vi.mocked(addressService.getUserAddresses).mockResolvedValue([home, office]);

    render(<AddressPage />);

    const homeCard = await screen.findByTestId('address-5');
    expect(within(homeCard).getByText('Default')).toBeInTheDocument();
    expect(within(homeCard).getByText('1 Main St, District 1, Ho Chi Minh City, Ho Chi Minh, 700000, Viet Nam'))
      .toBeInTheDocument();
    expect(within(homeCard).queryByRole('button', { name: /Make .* default/ })).not.toBeInTheDocument();
    const officeCard = screen.getByTestId('address-6');
    expect(within(officeCard).queryByText('Default')).not.toBeInTheDocument();
    expect(within(officeCard).getByText('2 Side St, Ho Chi Minh City, Viet Nam')).toBeInTheDocument();
  });

  it('shows the empty state', async () => {
    vi.mocked(addressService.getUserAddresses).mockResolvedValue([]);

    render(<AddressPage />);

    expect(await screen.findByText('You have no saved addresses yet.')).toBeInTheDocument();
  });

  it('shows a login prompt on 401/403', async () => {
    vi.mocked(addressService.getUserAddresses).mockRejectedValue(new ApiError(401, {}));

    render(<AddressPage />);

    expect(await screen.findByRole('link', { name: 'Log in to see your addresses' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/keycloak'
    );
  });

  it('make default calls the customer service and reloads', async () => {
    vi.mocked(addressService.getUserAddresses).mockResolvedValue([home, office]);
    vi.mocked(addressService.chooseDefaultAddress).mockResolvedValue(null);

    render(<AddressPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Make Office default' }));

    expect(addressService.chooseDefaultAddress).toHaveBeenCalledWith(6);
    await waitFor(() => expect(addressService.getUserAddresses).toHaveBeenCalledTimes(2));
  });

  it('delete asks for confirmation and deletes', async () => {
    vi.mocked(addressService.getUserAddresses).mockResolvedValue([home, office]);
    vi.mocked(addressService.deleteUserAddress).mockResolvedValue(null);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<AddressPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete Office' }));

    expect(window.confirm).toHaveBeenCalled();
    expect(addressService.deleteUserAddress).toHaveBeenCalledWith(6);
  });

  it('add address creates through the customer service', async () => {
    vi.mocked(addressService.getUserAddresses).mockResolvedValue([]);
    vi.mocked(addressService.createUserAddress).mockResolvedValue({});

    render(<AddressPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Add address' }));
    await screen.findByRole('option', { name: 'Viet Nam' });
    await userEvent.type(screen.getByLabelText('Contact name *'), 'Home');
    await userEvent.type(screen.getByLabelText('Phone *'), '0123456789');
    await userEvent.type(screen.getByLabelText('Address *'), '1 Main St');
    await userEvent.selectOptions(screen.getByLabelText('Country *'), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(addressService.createUserAddress).toHaveBeenCalledWith(
      expect.objectContaining({ contactName: 'Home', countryId: 1 })
    ));
    expect(addressService.updateAddress).not.toHaveBeenCalled();
  });

  it('edit updates the address in location', async () => {
    vi.mocked(addressService.getUserAddresses).mockResolvedValue([office]);
    vi.mocked(addressService.updateAddress).mockResolvedValue(null);

    render(<AddressPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Office' }));
    const name = screen.getByLabelText('Contact name *');
    await userEvent.clear(name);
    await userEvent.type(name, 'Office 2');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(addressService.updateAddress).toHaveBeenCalledWith(
      6, expect.objectContaining({ contactName: 'Office 2', addressLine1: '2 Side St', countryId: 1 })
    ));
  });
});
