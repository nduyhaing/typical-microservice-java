import { PAGE_SIZE } from '../../../common/models/Page';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { District, StateCountryName, StateOrProvince, StateOrProvincePage, StateOrProvincePost } from '../models/Location';

const base = '/location/backoffice/state-or-provinces';

/** All states or provinces of a country (selects). */
export const getStatesOrProvinces = (countryId: number) => get<StateOrProvince[]>(`${base}?countryId=${countryId}`);

/** A page of states sorted by name; without a country, the states of every country (3c-8). */
export const getStateOrProvincePage = (pageNo: number, countryId?: number) =>
  get<StateOrProvincePage>(
    `${base}/paging?pageNo=${pageNo}&pageSize=${PAGE_SIZE}${countryId === undefined ? '' : `&countryId=${countryId}`}`
  );

export const getStateOrProvince = (id: number) => get<StateOrProvince>(`${base}/${id}`);

export const createStateOrProvince = (state: StateOrProvincePost) => post<StateOrProvince>(base, state);

export const updateStateOrProvince = (id: number, state: StateOrProvincePost) => put<null>(`${base}/${id}`, state);

export const deleteStateOrProvince = (id: number) => del(`${base}/${id}`);

/** State and country names of the given state ids (at most 100, so one list page fits); no ids, no call. */
export async function getStateCountryNames(ids: number[]): Promise<StateCountryName[]> {
  const distinct = [...new Set(ids)];
  if (distinct.length === 0) {
    return [];
  }
  return get<StateCountryName[]>(`${base}/state-country-names?stateOrProvinceIds=${distinct.join(',')}`);
}

/** Districts of a state or province (read-only reference data). */
export const getDistricts = (stateOrProvinceId: number) => get<District[]>(`/location/backoffice/district/${stateOrProvinceId}`);
