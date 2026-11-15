import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as countryService from '../modules/location/services/CountryService';
import * as stateService from '../modules/location/services/StateOrProvinceService';
import * as taxClassService from '../modules/tax/services/TaxClassService';
import * as taxRateService from '../modules/tax/services/TaxRateService';
import TaxClassEditPage from '../pages/tax/tax-classes/[id]/edit';
import TaxClassCreatePage from '../pages/tax/tax-classes/create';
import TaxClassesPage from '../pages/tax/tax-classes/index';
import TaxRateEditPage from '../pages/tax/tax-rates/[id]/edit';
import TaxRateCreatePage from '../pages/tax/tax-rates/create';
import TaxRatesPage from '../pages/tax/tax-rates/index';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/location/services/CountryService');
vi.mock('../modules/location/services/StateOrProvinceService');
vi.mock('../modules/tax/services/TaxClassService');
vi.mock('../modules/tax/services/TaxRateService');

const standard = { id: 1, name: 'Standard' };
const reduced = { id: 2, name: 'Reduced' };
const country = (id: number, name: string) => ({
  id,
  code2: name.slice(0, 2).toUpperCase(),
  name,
  code3: null,
  isBillingEnabled: true,
  isShippingEnabled: true,
  isCityEnabled: true,
  isZipCodeEnabled: true,
  isDistrictEnabled: true,
});
const listPage = <T,>(key: string, content: T[]) => ({ [key]: content, pageNo: 0, pageSize: 10, totalElements: content.length, totalPages: 1, isLast: true });
const badRequest = (detail: string) => new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'TAX_CLASS_IN_USE', detail, fieldErrors: [] });

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
});
afterEach(() => {
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

describe('Tax classes', () => {
  it('lists tax classes and shows the in-use error on delete', async () => {
    vi.mocked(taxClassService.getTaxClassPage).mockResolvedValue(listPage('taxClassContent', [standard, reduced]) as never);
    vi.mocked(taxClassService.deleteTaxClass).mockRejectedValue(badRequest('Tax class 1 is used by tax rates'));
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<TaxClassesPage />);
    const row = (await screen.findByText('Standard')).closest('tr')!;
    expect(within(row).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/tax/tax-classes/1/edit');
    await userEvent.click(within(row).getByRole('button', { name: 'Delete' }));

    expect(window.confirm).toHaveBeenCalledWith('Delete tax class "Standard"?');
    expect(taxClassService.deleteTaxClass).toHaveBeenCalledWith(1);
    expect(await screen.findByRole('alert')).toHaveTextContent('Tax class 1 is used by tax rates');
    expect(screen.getByText('Standard')).toBeInTheDocument();
  });

  it('creates and edits a tax class', async () => {
    vi.mocked(taxClassService.createTaxClass).mockResolvedValue({ id: 3, name: 'Zero' });

    const { unmount } = render(<TaxClassCreatePage />);
    await userEvent.type(screen.getByLabelText('Name'), ' Zero ');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(taxClassService.createTaxClass).toHaveBeenCalledWith({ name: 'Zero' });
    expect(push).toHaveBeenCalledWith('/tax/tax-classes');
    unmount();

    router.query = { id: '2' };
    vi.mocked(taxClassService.getTaxClass).mockResolvedValue(reduced);
    vi.mocked(taxClassService.updateTaxClass).mockResolvedValue(null);
    render(<TaxClassEditPage />);
    const name = await screen.findByLabelText('Name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Reduced rate');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(taxClassService.updateTaxClass).toHaveBeenCalledWith(2, { name: 'Reduced rate' });
  });
});

describe('Tax rates', () => {
  it('lists rates with their tax class, country and state names', async () => {
    vi.mocked(taxRateService.getTaxRateRows).mockResolvedValue({
      page: { taxRateGetDetailContent: [], pageNo: 0, pageSize: 10, totalElements: 2, totalPages: 1, isLast: true },
      rows: [
        { id: 1, rate: 10, zipCode: null, taxClassId: 1, taxClassName: 'Standard', stateOrProvinceId: 5, countryId: 1, countryName: 'Viet Nam', stateOrProvinceName: 'Ha Noi' },
        { id: 2, rate: 8.875, zipCode: '10001', taxClassId: 2, taxClassName: 'Reduced', stateOrProvinceId: null, countryId: 2, countryName: 'United States', stateOrProvinceName: null },
      ],
      namesUnavailable: false,
    });

    render(<TaxRatesPage />);

    const first = (await screen.findByText('Ha Noi')).closest('tr')!;
    expect(within(first).getByText('Viet Nam')).toBeInTheDocument();
    expect(within(first).getByText('Standard')).toBeInTheDocument();
    expect(within(first).getByText('10')).toBeInTheDocument();
    const second = screen.getByText('United States').closest('tr')!;
    expect(within(second).getByText('8.875')).toBeInTheDocument();
    expect(within(second).getByText('10001')).toBeInTheDocument();
    expect(within(second).getByText('Any')).toBeInTheDocument();
    expect(within(second).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/tax/tax-rates/2/edit');
  });

  it('warns when location names could not be loaded', async () => {
    vi.mocked(taxRateService.getTaxRateRows).mockResolvedValue({
      page: { taxRateGetDetailContent: [], pageNo: 0, pageSize: 10, totalElements: 1, totalPages: 1, isLast: true },
      rows: [{ id: 1, rate: 10, zipCode: null, taxClassId: 1, taxClassName: 'Standard', stateOrProvinceId: null, countryId: 7, countryName: 'Country #7', stateOrProvinceName: null }],
      namesUnavailable: true,
    });

    render(<TaxRatesPage />);

    expect(await screen.findByText('Country #7')).toBeInTheDocument();
    expect(screen.getByText(/Country and state names could not be loaded/)).toBeInTheDocument();
  });

  describe('form', () => {
    beforeEach(() => {
      vi.mocked(taxClassService.getTaxClasses).mockResolvedValue([standard, reduced]);
      vi.mocked(countryService.getCountries).mockResolvedValue([country(1, 'Viet Nam'), country(2, 'United States')]);
      vi.mocked(stateService.getStatesOrProvinces).mockImplementation(async (countryId) =>
        countryId === 1 ? [{ id: 5, name: 'Ha Noi', code: null, type: null, countryId: 1 }] : []
      );
    });

    it('creates a rate for a country and state', async () => {
      vi.mocked(taxRateService.createTaxRate).mockResolvedValue({ id: 3, rate: 8.875, zipCode: '', taxClassId: 2, stateOrProvinceId: 5, countryId: 1 });

      render(<TaxRateCreatePage />);
      await screen.findByRole('option', { name: 'Reduced' });
      await screen.findByRole('option', { name: 'Viet Nam' });
      await userEvent.selectOptions(screen.getByLabelText('Tax class'), 'Reduced');
      await userEvent.selectOptions(screen.getByLabelText('Country'), 'Viet Nam');
      await userEvent.selectOptions(await screen.findByLabelText('State or province'), await screen.findByRole('option', { name: 'Ha Noi' }));
      await userEvent.type(screen.getByLabelText('Rate (%)'), '8.875');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(taxRateService.createTaxRate).toHaveBeenCalledWith({ rate: 8.875, zipCode: '', taxClassId: 2, stateOrProvinceId: 5, countryId: 1 });
      expect(push).toHaveBeenCalledWith('/tax/tax-rates');
    });

    it('refuses a rate above 100 or with more than 4 decimals', async () => {
      render(<TaxRateCreatePage />);
      await screen.findByRole('option', { name: 'Viet Nam' });
      await userEvent.selectOptions(screen.getByLabelText('Tax class'), 'Standard');
      await userEvent.selectOptions(screen.getByLabelText('Country'), 'United States');
      await userEvent.type(screen.getByLabelText('Rate (%)'), '1.23456');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(screen.getByText('Tax class, country and a rate from 0 to 100 (at most 4 decimals) are required.')).toBeInTheDocument();
      expect(taxRateService.createTaxRate).not.toHaveBeenCalled();
    });

    it('edits a rate; changing the country clears the state', async () => {
      router.query = { id: '3' };
      vi.mocked(taxRateService.getTaxRate).mockResolvedValue({ id: 3, rate: 10, zipCode: '100000', taxClassId: 1, stateOrProvinceId: 5, countryId: 1 });
      vi.mocked(taxRateService.updateTaxRate).mockResolvedValue(null);

      render(<TaxRateEditPage />);
      await screen.findByRole('option', { name: 'Ha Noi' });
      expect(screen.getByLabelText('State or province')).toHaveValue('5');
      expect(screen.getByLabelText('Rate (%)')).toHaveValue('10');
      await userEvent.selectOptions(screen.getByLabelText('Country'), 'United States');
      expect(screen.getByLabelText('State or province')).toHaveValue('');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(taxRateService.updateTaxRate).toHaveBeenCalledWith(3, { rate: 10, zipCode: '100000', taxClassId: 1, stateOrProvinceId: null, countryId: 2 });
    });

    it('shows a duplicate rate error from the server', async () => {
      vi.mocked(taxRateService.createTaxRate).mockRejectedValue(badRequest('Tax rate already exists'));

      render(<TaxRateCreatePage />);
      await screen.findByRole('option', { name: 'Viet Nam' });
      await userEvent.selectOptions(screen.getByLabelText('Tax class'), 'Standard');
      await userEvent.selectOptions(screen.getByLabelText('Country'), 'United States');
      await userEvent.type(screen.getByLabelText('Rate (%)'), '5');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('Tax rate already exists');
    });
  });
});
