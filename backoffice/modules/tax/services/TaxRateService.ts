import { PAGE_SIZE } from '../../../common/models/Page';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { getCountries } from '../../location/services/CountryService';
import { getStateCountryNames } from '../../location/services/StateOrProvinceService';
import { TaxRate, TaxRatePage, TaxRatePost, TaxRateRow } from '../models/Tax';

const base = '/tax/backoffice/tax-rates';

export const getTaxRatePage = (pageNo: number) => get<TaxRatePage>(`${base}/paging?pageNo=${pageNo}&pageSize=${PAGE_SIZE}`);

export const getTaxRate = (id: number) => get<TaxRate>(`${base}/${id}`);

export const createTaxRate = (rate: TaxRatePost) => post<TaxRate>(base, rate);

export const updateTaxRate = (id: number, rate: TaxRatePost) => put<null>(`${base}/${id}`, rate);

export const deleteTaxRate = (id: number) => del(`${base}/${id}`);

/**
 * A page of rates with country and state names: tax only returns their ids (3c-15), so the names come from location
 * (all countries, and `state-country-names` for the page's states). When location cannot answer, the rows still show,
 * named by id, and `namesUnavailable` is set.
 */
export async function getTaxRateRows(pageNo: number) {
  const page = await getTaxRatePage(pageNo);
  const rates = page.taxRateGetDetailContent;
  const stateIds = rates.flatMap((r) => (r.stateOrProvinceId == null ? [] : [r.stateOrProvinceId]));
  const [countries, states] = await Promise.allSettled([
    getCountries(),
    getStateCountryNames(stateIds),
  ]);
  const countryNames = new Map(countries.status === 'fulfilled' ? countries.value.map((c) => [c.id, c.name]) : []);
  const stateNames = new Map(
    states.status === 'fulfilled' ? states.value.map((s) => [s.stateOrProvinceId, s.stateOrProvinceName]) : []
  );
  const rows: TaxRateRow[] = rates.map((rate) => ({
    ...rate,
    countryName: countryNames.get(rate.countryId) ?? `Country #${rate.countryId}`,
    stateOrProvinceName:
      rate.stateOrProvinceId == null
        ? null
        : (stateNames.get(rate.stateOrProvinceId) ?? `State #${rate.stateOrProvinceId}`),
  }));
  return { page, rows, namesUnavailable: countries.status === 'rejected' || states.status === 'rejected' };
}
