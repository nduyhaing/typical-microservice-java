import { vi } from 'vitest';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import * as countryService from '../../location/services/CountryService';
import * as stateService from '../../location/services/StateOrProvinceService';
import {
  createTaxClass,
  deleteTaxClass,
  getTaxClass,
  getTaxClasses,
  getTaxClassPage,
  updateTaxClass,
} from './TaxClassService';
import {
  createTaxRate,
  deleteTaxRate,
  getTaxRate,
  getTaxRatePage,
  getTaxRateRows,
  updateTaxRate,
} from './TaxRateService';

vi.mock('../../../common/services/ApiClientService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../common/services/ApiClientService')>()),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}));
vi.mock('../../location/services/CountryService');
vi.mock('../../location/services/StateOrProvinceService');

afterEach(() => vi.resetAllMocks());

describe('TaxClassService', () => {
  it('calls the tax class endpoints', async () => {
    await getTaxClasses();
    await getTaxClassPage(1);
    await getTaxClass(4);
    await createTaxClass({ name: 'Standard' });
    await updateTaxClass(4, { name: 'Reduced' });
    await deleteTaxClass(4);

    expect(get).toHaveBeenNthCalledWith(1, '/tax/backoffice/tax-classes');
    expect(get).toHaveBeenNthCalledWith(2, '/tax/backoffice/tax-classes/paging?pageNo=1&pageSize=10');
    expect(get).toHaveBeenNthCalledWith(3, '/tax/backoffice/tax-classes/4');
    expect(post).toHaveBeenCalledWith('/tax/backoffice/tax-classes', { name: 'Standard' });
    expect(put).toHaveBeenCalledWith('/tax/backoffice/tax-classes/4', { name: 'Reduced' });
    expect(del).toHaveBeenCalledWith('/tax/backoffice/tax-classes/4');
  });
});

describe('TaxRateService', () => {
  const rate = { rate: 8.875, zipCode: '10001', taxClassId: 1, stateOrProvinceId: 2, countryId: 3 };

  it('calls the tax rate endpoints', async () => {
    await getTaxRatePage(0);
    await getTaxRate(9);
    await createTaxRate(rate);
    await updateTaxRate(9, rate);
    await deleteTaxRate(9);

    expect(get).toHaveBeenNthCalledWith(1, '/tax/backoffice/tax-rates/paging?pageNo=0&pageSize=10');
    expect(get).toHaveBeenNthCalledWith(2, '/tax/backoffice/tax-rates/9');
    expect(post).toHaveBeenCalledWith('/tax/backoffice/tax-rates', rate);
    expect(put).toHaveBeenCalledWith('/tax/backoffice/tax-rates/9', rate);
    expect(del).toHaveBeenCalledWith('/tax/backoffice/tax-rates/9');
  });

  it('resolves country and state names through location', async () => {
    vi.mocked(get).mockResolvedValue({
      taxRateGetDetailContent: [
        { id: 1, rate: 10.0, zipCode: null, taxClassId: 1, taxClassName: 'Standard', stateOrProvinceId: 5, countryId: 2 },
        { id: 2, rate: 7.5, zipCode: '700000', taxClassId: 1, taxClassName: 'Standard', stateOrProvinceId: null, countryId: 9 },
      ],
      pageNo: 0,
      pageSize: 10,
      totalElements: 2,
      totalPages: 1,
      isLast: true,
    });
    vi.mocked(countryService.getCountries).mockResolvedValue([
      { id: 2, code2: 'VN', name: 'Viet Nam', code3: 'VNM', isBillingEnabled: true, isShippingEnabled: true, isCityEnabled: true, isZipCodeEnabled: true, isDistrictEnabled: true },
    ]);
    vi.mocked(stateService.getStateCountryNames).mockResolvedValue([
      { stateOrProvinceId: 5, stateOrProvinceName: 'Ha Noi', countryName: 'Viet Nam' },
    ]);

    const result = await getTaxRateRows(0);

    expect(stateService.getStateCountryNames).toHaveBeenCalledWith([5]);
    expect(result.namesUnavailable).toBe(false);
    expect(result.rows.map((r) => [r.countryName, r.stateOrProvinceName])).toEqual([
      ['Viet Nam', 'Ha Noi'],
      ['Country #9', null],
    ]);
  });

  it('still lists the rates with ids when location cannot answer', async () => {
    vi.mocked(get).mockResolvedValue({
      taxRateGetDetailContent: [
        { id: 1, rate: 10.0, zipCode: null, taxClassId: 1, taxClassName: 'Standard', stateOrProvinceId: 5, countryId: 2 },
      ],
      pageNo: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      isLast: true,
    });
    vi.mocked(countryService.getCountries).mockRejectedValue(new Error('down'));
    vi.mocked(stateService.getStateCountryNames).mockRejectedValue(new Error('down'));

    const result = await getTaxRateRows(0);

    expect(result.namesUnavailable).toBe(true);
    expect(result.rows[0]).toMatchObject({ countryName: 'Country #2', stateOrProvinceName: 'State #5' });
  });
});
