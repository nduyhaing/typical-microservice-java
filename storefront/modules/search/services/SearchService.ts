import { get } from '../../../common/services/ApiClientService';
import { ProductListGet, ProductNameList, SearchParams } from '../models/ProductSearch';
import { toApiQuery } from '../utils/searchQuery';

const base = '/search/storefront';

export const searchProducts = (params: SearchParams) =>
  get<ProductListGet>(`${base}/catalog-search?${toApiQuery(params)}`);

export const getSuggestions = (keyword: string, signal?: AbortSignal) =>
  get<ProductNameList>(`${base}/search_suggest?${new URLSearchParams({ keyword })}`, signal);
