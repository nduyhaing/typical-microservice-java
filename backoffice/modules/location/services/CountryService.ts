import { PAGE_SIZE } from '../../../common/models/Page';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { Country, CountryPage, CountryPost } from '../models/Location';

const base = '/location/backoffice/countries';

/** All countries (selects, name lookups). */
export const getCountries = () => get<Country[]>(base);

export const getCountryPage = (pageNo: number) => get<CountryPage>(`${base}/paging?pageNo=${pageNo}&pageSize=${PAGE_SIZE}`);

export const getCountry = (id: number) => get<Country>(`${base}/${id}`);

export const createCountry = (country: CountryPost) => post<Country>(base, country);

export const updateCountry = (id: number, country: CountryPost) => put<null>(`${base}/${id}`, country);

export const deleteCountry = (id: number) => del(`${base}/${id}`);
