import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as countryService from '../modules/location/services/CountryService';
import * as stateService from '../modules/location/services/StateOrProvinceService';
import CountryEditPage from '../pages/location/countries/[id]/edit';
import CountryCreatePage from '../pages/location/countries/create';
import CountriesPage from '../pages/location/countries/index';
import StateEditPage from '../pages/location/state-or-provinces/[id]/edit';
import StateCreatePage from '../pages/location/state-or-provinces/create';
import StatesPage from '../pages/location/state-or-provinces/index';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/location/services/CountryService');
vi.mock('../modules/location/services/StateOrProvinceService');

const vn = {
  id: 1,
  code2: 'VN',
  name: 'Viet Nam',
  code3: 'VNM',
  isBillingEnabled: true,
  isShippingEnabled: true,
  isCityEnabled: true,
  isZipCodeEnabled: false,
  isDistrictEnabled: true,
};
const fr = { ...vn, id: 2, code2: 'FR', name: 'France', code3: 'FRA', isDistrictEnabled: false };
const page = <T,>(key: string, content: T[], pageNo = 0, totalPages = 1) => ({
  [key]: content,
  pageNo,
  pageSize: 10,
  totalElements: content.length,
  totalPages,
  isLast: pageNo >= totalPages - 1,
});
const inUse = (detail: string) => new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'Bad Request', detail, fieldErrors: [] });

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
});
afterEach(() => {
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

describe('Countries', () => {
  it('lists a page of countries with edit links', async () => {
    vi.mocked(countryService.getCountryPage).mockResolvedValue(page('countryContent', [vn, fr], 0, 25) as never);

    render(<CountriesPage />);

    const row = (await screen.findByText('Viet Nam')).closest('tr')!;
    expect(within(row).getByText('VNM')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/location/countries/1/edit');
    expect(countryService.getCountryPage).toHaveBeenCalledWith(0);
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(countryService.getCountryPage).toHaveBeenLastCalledWith(1);
  });

  it('shows the server message when a country in use cannot be deleted', async () => {
    vi.mocked(countryService.getCountryPage).mockResolvedValue(page('countryContent', [vn]) as never);
    vi.mocked(countryService.deleteCountry).mockRejectedValue(inUse('Country 1 is in use and cannot be deleted'));
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<CountriesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(window.confirm).toHaveBeenCalledWith('Delete country "Viet Nam"?');
    expect(countryService.deleteCountry).toHaveBeenCalledWith(1);
    expect(await screen.findByRole('alert')).toHaveTextContent('Country 1 is in use and cannot be deleted');
  });

  it('creates a country with its codes and flags', async () => {
    vi.mocked(countryService.createCountry).mockResolvedValue({ ...fr, id: 9 });

    render(<CountryCreatePage />);
    await userEvent.type(screen.getByLabelText('Name'), ' Germany ');
    await userEvent.type(screen.getByLabelText('Code 2'), 'de');
    await userEvent.type(screen.getByLabelText('Code 3'), 'deu');
    await userEvent.click(screen.getByLabelText('Shipping enabled'));
    await userEvent.click(screen.getByLabelText('Zip code enabled'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(countryService.createCountry).toHaveBeenCalledWith({
      name: 'Germany',
      code2: 'DE',
      code3: 'DEU',
      isBillingEnabled: false,
      isShippingEnabled: true,
      isCityEnabled: false,
      isZipCodeEnabled: true,
      isDistrictEnabled: false,
    });
    expect(push).toHaveBeenCalledWith('/location/countries');
  });

  it('shows the duplicate error from the server', async () => {
    vi.mocked(countryService.createCountry).mockRejectedValue(inUse('Country code VN is already existed'));

    render(<CountryCreatePage />);
    await userEvent.type(screen.getByLabelText('Name'), 'Vietnam');
    await userEvent.type(screen.getByLabelText('Code 2'), 'VN');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Country code VN is already existed');
    expect(push).not.toHaveBeenCalled();
  });

  it('edits a country keeping every field (PUT replaces all of them)', async () => {
    router.query = { id: '1' };
    vi.mocked(countryService.getCountry).mockResolvedValue(vn);
    vi.mocked(countryService.updateCountry).mockResolvedValue(null);

    render(<CountryEditPage />);
    await userEvent.clear(await screen.findByLabelText('Code 3'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(countryService.updateCountry).toHaveBeenCalledWith(1, {
      name: 'Viet Nam',
      code2: 'VN',
      code3: null,
      isBillingEnabled: true,
      isShippingEnabled: true,
      isCityEnabled: true,
      isZipCodeEnabled: false,
      isDistrictEnabled: true,
    });
  });
});

describe('States or provinces', () => {
  const hanoi = { id: 10, name: 'Ha Noi', code: 'HN', type: 'City', countryId: 1 };

  beforeEach(() => {
    vi.mocked(countryService.getCountries).mockResolvedValue([vn, fr]);
  });

  it('lists states with their country, filters by country and shows the districts read-only', async () => {
    vi.mocked(stateService.getStateOrProvincePage).mockResolvedValue(page('stateOrProvinceContent', [hanoi]) as never);
    vi.mocked(stateService.getDistricts).mockResolvedValue([
      { id: 100, name: 'Ba Dinh' },
      { id: 101, name: 'Hoan Kiem' },
    ]);

    render(<StatesPage />);

    const row = (await screen.findByText('Ha Noi')).closest('tr')!;
    expect(await within(row).findByText('Viet Nam')).toBeInTheDocument();
    expect(stateService.getStateOrProvincePage).toHaveBeenCalledWith(0, undefined);
    await userEvent.selectOptions(screen.getByLabelText('Country'), 'Viet Nam');
    expect(stateService.getStateOrProvincePage).toHaveBeenLastCalledWith(0, 1);

    await userEvent.click(await screen.findByRole('button', { name: 'Districts' }));
    const dialog = await screen.findByRole('dialog');
    expect(stateService.getDistricts).toHaveBeenCalledWith(10);
    expect(await within(dialog).findByText('Hoan Kiem')).toBeInTheDocument();
    expect(within(dialog).getByText('Districts of Ha Noi')).toBeInTheDocument();
  });

  it('shows the server message when a state in use cannot be deleted', async () => {
    vi.mocked(stateService.getStateOrProvincePage).mockResolvedValue(page('stateOrProvinceContent', [hanoi]) as never);
    vi.mocked(stateService.deleteStateOrProvince).mockRejectedValue(inUse('State or province 10 is in use'));
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<StatesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('State or province 10 is in use');
  });

  it('creates a state in the chosen country', async () => {
    vi.mocked(stateService.createStateOrProvince).mockResolvedValue({ ...hanoi, id: 11 });

    render(<StateCreatePage />);
    await screen.findByRole('option', { name: 'France' });
    await userEvent.selectOptions(screen.getByLabelText('Country'), 'France');
    await userEvent.type(screen.getByLabelText('Name'), 'Bretagne');
    await userEvent.type(screen.getByLabelText('Code'), 'BRE');
    await userEvent.type(screen.getByLabelText('Type'), 'Region');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(stateService.createStateOrProvince).toHaveBeenCalledWith({ name: 'Bretagne', code: 'BRE', type: 'Region', countryId: 2 });
    expect(push).toHaveBeenCalledWith('/location/state-or-provinces');
  });

  it('requires a country for a new state', async () => {
    render(<StateCreatePage />);
    await userEvent.type(screen.getByLabelText('Name'), 'Bretagne');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('Country and name are required.')).toBeInTheDocument();
    expect(stateService.createStateOrProvince).not.toHaveBeenCalled();
  });

  it('edits a state; its country cannot change', async () => {
    router.query = { id: '10' };
    vi.mocked(stateService.getStateOrProvince).mockResolvedValue(hanoi);
    vi.mocked(stateService.updateStateOrProvince).mockResolvedValue(null);

    render(<StateEditPage />);
    const name = await screen.findByLabelText('Name');
    expect(screen.getByLabelText('Country')).toBeDisabled();
    await userEvent.clear(name);
    await userEvent.type(name, 'Hanoi');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(stateService.updateStateOrProvince).toHaveBeenCalledWith(10, { name: 'Hanoi', code: 'HN', type: 'City', countryId: 1 });
  });
});
