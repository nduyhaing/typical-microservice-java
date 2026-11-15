import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import * as addressService from '../services/AddressService';
import AddressForm from './AddressForm';

vi.mock('../services/AddressService');

const countries = [
  { id: 1, code2: 'VN', name: 'Viet Nam' },
  { id: 2, code2: 'US', name: 'United States' },
];
const statesByCountry: Record<number, { id: number; name: string; countryId: number }[]> = {
  1: [{ id: 10, name: 'Ho Chi Minh', countryId: 1 }],
  2: [{ id: 20, name: 'California', countryId: 2 }],
};
const districtsByState: Record<number, { id: number; name: string }[]> = {
  10: [{ id: 100, name: 'District 1' }],
  20: [],
};

const saved = {
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

describe('AddressForm', () => {
  beforeEach(() => {
    vi.mocked(addressService.getCountries).mockResolvedValue(countries);
    vi.mocked(addressService.getStatesOrProvinces).mockImplementation(async (id) => statesByCountry[id] ?? []);
    vi.mocked(addressService.getDistricts).mockImplementation(async (id) => districtsByState[id] ?? []);
  });
  afterEach(() => vi.resetAllMocks());

  const country = () => screen.getByLabelText('Country *');
  const state = () => screen.getByLabelText('State or province');
  const district = () => screen.getByLabelText('District');

  it('loads states for the chosen country and districts for the chosen state', async () => {
    render(<AddressForm onSubmit={vi.fn()} />);
    await screen.findByRole('option', { name: 'Viet Nam' });
    expect(state()).toBeDisabled();

    await userEvent.selectOptions(country(), '1');
    await screen.findByRole('option', { name: 'Ho Chi Minh' });
    expect(addressService.getStatesOrProvinces).toHaveBeenCalledWith(1);

    await userEvent.selectOptions(state(), '10');
    await screen.findByRole('option', { name: 'District 1' });
    expect(addressService.getDistricts).toHaveBeenCalledWith(10);
  });

  it('changing the country clears state and district', async () => {
    render(<AddressForm initial={saved} onSubmit={vi.fn()} />);
    await screen.findByRole('option', { name: 'District 1' });
    expect(state()).toHaveValue('10');
    expect(district()).toHaveValue('100');

    await userEvent.selectOptions(country(), '2');

    await screen.findByRole('option', { name: 'California' });
    expect(state()).toHaveValue('');
    expect(district()).toHaveValue('');
    expect(screen.queryByRole('option', { name: 'District 1' })).not.toBeInTheDocument();
  });

  it('submits the payload with empty optional fields as null', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<AddressForm onSubmit={onSubmit} />);
    await screen.findByRole('option', { name: 'Viet Nam' });

    await userEvent.type(screen.getByLabelText('Contact name *'), ' Customer Dev ');
    await userEvent.type(screen.getByLabelText('Phone *'), '0123456789');
    await userEvent.type(screen.getByLabelText('Address *'), '1 Main St');
    await userEvent.selectOptions(country(), '1');
    await screen.findByRole('option', { name: 'Ho Chi Minh' });
    await userEvent.selectOptions(state(), '10');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith({
      contactName: 'Customer Dev',
      phone: '0123456789',
      addressLine1: '1 Main St',
      addressLine2: null,
      city: null,
      zipCode: null,
      countryId: 1,
      stateOrProvinceId: 10,
      districtId: null,
    });
  });

  it('does not submit without the required fields', async () => {
    const onSubmit = vi.fn();
    render(<AddressForm onSubmit={onSubmit} />);
    await screen.findByRole('option', { name: 'Viet Nam' });

    await userEvent.type(screen.getByLabelText('Contact name *'), 'Customer Dev');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Please fill in contact name, phone, address and country.')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('shows the server message when saving fails', async () => {
    const onSubmit = vi.fn().mockRejectedValue({ body: { detail: 'State 10 is not in country 2' } });
    render(<AddressForm initial={saved} onSubmit={onSubmit} />);
    await screen.findByRole('option', { name: 'District 1' });

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('State 10 is not in country 2')).toBeInTheDocument();
  });
});
