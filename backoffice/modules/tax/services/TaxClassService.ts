import { PAGE_SIZE } from '../../../common/models/Page';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { TaxClass, TaxClassPage, TaxClassPost } from '../models/Tax';

const base = '/tax/backoffice/tax-classes';

/** All tax classes, sorted by name (selects). */
export const getTaxClasses = () => get<TaxClass[]>(base);

export const getTaxClassPage = (pageNo: number) => get<TaxClassPage>(`${base}/paging?pageNo=${pageNo}&pageSize=${PAGE_SIZE}`);

export const getTaxClass = (id: number) => get<TaxClass>(`${base}/${id}`);

export const createTaxClass = (taxClass: TaxClassPost) => post<TaxClass>(base, taxClass);

export const updateTaxClass = (id: number, taxClass: TaxClassPost) => put<null>(`${base}/${id}`, taxClass);

export const deleteTaxClass = (id: number) => del(`${base}/${id}`);
