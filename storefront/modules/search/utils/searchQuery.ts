import { ParsedUrlQuery } from 'querystring';
import { SORT_TYPES, SearchParams, SortType } from '../models/ProductSearch';

/** Hits per page on the search page. */
export const PAGE_SIZE = 12;

// Bounds of the search service (decisions.md 4-14): out-of-range input is dropped or clamped here, so a hand-edited
// URL still gets results instead of a 400 that would look like "search is down".
const MAX_KEYWORD = 100;
const MAX_FILTER_VALUES = 20;
const MAX_FILTER_VALUE_LENGTH = 100;
const MAX_PRICE = 1_000_000_000;
const MAX_PAGE = Math.floor(10_000 / PAGE_SIZE) - 1;

export const FILTERS = ['brand', 'category', 'attribute'] as const;
export type Filter = (typeof FILTERS)[number];

const values = (value: string | string[] | undefined): string[] =>
  value === undefined ? [] : Array.isArray(value) ? value : [value];

const first = (value: string | string[] | undefined): string | undefined => values(value)[0];

/**
 * Filter values travel as repeated URL parameters (`brand=A&brand=B`) but as one comma-separated parameter to the
 * search service, so a value containing a comma can't be sent and is dropped.
 */
function filterValues(value: string | string[] | undefined): string[] {
  const cleaned = values(value)
    .map((v) => v.trim())
    .filter((v) => v && !v.includes(',') && v.length <= MAX_FILTER_VALUE_LENGTH);
  return [...new Set(cleaned)].slice(0, MAX_FILTER_VALUES);
}

function price(value: string | string[] | undefined): number | undefined {
  const raw = first(value);
  if (raw === undefined || raw.trim() === '') {
    return undefined;
  }
  const number = Number(raw);
  return Number.isFinite(number) && number >= 0 ? Math.min(number, MAX_PRICE) : undefined;
}

function page(value: string | string[] | undefined): number {
  const number = Number.parseInt(first(value) ?? '', 10);
  return Number.isFinite(number) && number > 0 ? Math.min(number, MAX_PAGE) : 0;
}

/** URL query → search state; never throws, whatever the URL holds. */
export function parseSearchQuery(query: ParsedUrlQuery): SearchParams {
  const sortType = first(query.sortType);
  return {
    keyword: (first(query.keyword) ?? '').trim().slice(0, MAX_KEYWORD),
    page: page(query.page),
    brand: filterValues(query.brand),
    category: filterValues(query.category),
    attribute: filterValues(query.attribute),
    minPrice: price(query.minPrice),
    maxPrice: price(query.maxPrice),
    sortType: SORT_TYPES.includes(sortType as SortType) ? (sortType as SortType) : 'DEFAULT',
  };
}

/** Search state → query string for `GET /search/storefront/catalog-search` (URLSearchParams encodes it). */
export function toApiQuery(params: SearchParams): string {
  const query = new URLSearchParams({
    keyword: params.keyword,
    page: String(params.page),
    size: String(PAGE_SIZE),
    sortType: params.sortType,
  });
  for (const filter of FILTERS) {
    if (params[filter].length > 0) {
      query.set(filter, params[filter].join(','));
    }
  }
  if (params.minPrice !== undefined) {
    query.set('minPrice', String(params.minPrice));
  }
  if (params.maxPrice !== undefined) {
    query.set('maxPrice', String(params.maxPrice));
  }
  return query.toString();
}

/** Search state → the page's URL query for `router.push`; defaults are left out. */
export function toUrlQuery(params: SearchParams): Record<string, string | string[]> {
  const query: Record<string, string | string[]> = {};
  if (params.keyword) {
    query.keyword = params.keyword;
  }
  for (const filter of FILTERS) {
    if (params[filter].length > 0) {
      query[filter] = params[filter];
    }
  }
  if (params.minPrice !== undefined) {
    query.minPrice = String(params.minPrice);
  }
  if (params.maxPrice !== undefined) {
    query.maxPrice = String(params.maxPrice);
  }
  if (params.sortType !== 'DEFAULT') {
    query.sortType = params.sortType;
  }
  if (params.page > 0) {
    query.page = String(params.page);
  }
  return query;
}
