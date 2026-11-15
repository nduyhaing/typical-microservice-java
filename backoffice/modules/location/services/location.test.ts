import { vi } from 'vitest';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import {
  createCountry,
  deleteCountry,
  getCountries,
  getCountry,
  getCountryPage,
  updateCountry,
} from './CountryService';
import {
  createStateOrProvince,
  deleteStateOrProvince,
  getDistricts,
  getStateCountryNames,
  getStateOrProvince,
  getStateOrProvincePage,
  getStatesOrProvinces,
  updateStateOrProvince,
} from './StateOrProvinceService';

vi.mock('../../../common/services/ApiClientService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../common/services/ApiClientService')>()),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}));

afterEach(() => vi.resetAllMocks());

const country = {
  code2: 'FR',
  name: 'France',
  code3: 'FRA',
  isBillingEnabled: true,
  isShippingEnabled: true,
  isCityEnabled: false,
  isZipCodeEnabled: true,
  isDistrictEnabled: false,
};

describe('CountryService', () => {
  it('calls the location back office country endpoints', async () => {
    await getCountries();
    await getCountryPage(2);
    await getCountry(5);
    await createCountry(country);
    await updateCountry(5, country);
    await deleteCountry(5);

    expect(get).toHaveBeenNthCalledWith(1, '/location/backoffice/countries');
    expect(get).toHaveBeenNthCalledWith(2, '/location/backoffice/countries/paging?pageNo=2&pageSize=10');
    expect(get).toHaveBeenNthCalledWith(3, '/location/backoffice/countries/5');
    expect(post).toHaveBeenCalledWith('/location/backoffice/countries', country);
    expect(put).toHaveBeenCalledWith('/location/backoffice/countries/5', country);
    expect(del).toHaveBeenCalledWith('/location/backoffice/countries/5');
  });
});

describe('StateOrProvinceService', () => {
  const state = { name: 'Bavaria', code: 'BY', type: 'State', countryId: 3 };

  it('calls the location back office state endpoints', async () => {
    await getStatesOrProvinces(3);
    await getStateOrProvincePage(1, 3);
    await getStateOrProvincePage(0);
    await getStateOrProvince(7);
    await createStateOrProvince(state);
    await updateStateOrProvince(7, state);
    await deleteStateOrProvince(7);
    await getDistricts(7);

    expect(get).toHaveBeenNthCalledWith(1, '/location/backoffice/state-or-provinces?countryId=3');
    expect(get).toHaveBeenNthCalledWith(2, '/location/backoffice/state-or-provinces/paging?pageNo=1&pageSize=10&countryId=3');
    expect(get).toHaveBeenNthCalledWith(3, '/location/backoffice/state-or-provinces/paging?pageNo=0&pageSize=10');
    expect(get).toHaveBeenNthCalledWith(4, '/location/backoffice/state-or-provinces/7');
    expect(post).toHaveBeenCalledWith('/location/backoffice/state-or-provinces', state);
    expect(put).toHaveBeenCalledWith('/location/backoffice/state-or-provinces/7', state);
    expect(del).toHaveBeenCalledWith('/location/backoffice/state-or-provinces/7');
    expect(get).toHaveBeenNthCalledWith(5, '/location/backoffice/district/7');
  });

  it('asks for state and country names of distinct ids, and not at all without ids', async () => {
    vi.mocked(get).mockResolvedValue([{ stateOrProvinceId: 1, stateOrProvinceName: 'Ha Noi', countryName: 'Viet Nam' }]);

    await expect(getStateCountryNames([])).resolves.toEqual([]);
    expect(get).not.toHaveBeenCalled();

    await getStateCountryNames([1, 2, 1]);
    expect(get).toHaveBeenCalledWith('/location/backoffice/state-or-provinces/state-country-names?stateOrProvinceIds=1,2');
  });
});
