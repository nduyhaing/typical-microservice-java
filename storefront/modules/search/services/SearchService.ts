import { get } from '../../../common/services/ApiClientService';
import { ProductListGet, ProductNameList, SearchParams } from '../models/ProductSearch';
import { toApiQuery } from '../utils/searchQuery';

const base = '/search/storefront';

/**
 * How long the server-rendered search page waits for the search service; on timeout it renders "Search is not
 * available right now" (decisions.md 4-78).
 */
export const SEARCH_TIMEOUT_MS = 3000;

export const searchProducts = (params: SearchParams, signal: AbortSignal = AbortSignal.timeout(SEARCH_TIMEOUT_MS)) =>
  get<ProductListGet>(`${base}/catalog-search?${toApiQuery(params)}`, signal);

export const getSuggestions = (keyword: string, signal?: AbortSignal) =>
  get<ProductNameList>(`${base}/search_suggest?${new URLSearchParams({ keyword })}`, signal);
