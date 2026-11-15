/** One hit of `GET /search/storefront/catalog-search` (YAS ProductGetVm plus thumbnailUrl). */
export type ProductSearchHit = {
  id: number;
  name: string;
  slug: string;
  thumbnailId: number | null;
  thumbnailUrl: string | null;
  price: number | null;
  isAllowedToOrder: boolean | null;
  isPublished: boolean | null;
  isFeatured: boolean | null;
  isVisibleIndividually: boolean | null;
  createdOn: string | null;
};

/** YAS ProductListGetVm: a page of hits plus facet counts (`brands`, `categories`, `attributes`: value → count). */
export type ProductListGet = {
  products: ProductSearchHit[];
  pageNo: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
  aggregations: Record<string, Record<string, number>>;
};

/** YAS ProductNameListVm (`GET /search/storefront/search_suggest`). */
export type ProductNameList = { productNames: { name: string }[] };

export const SORT_TYPES = ['DEFAULT', 'PRICE_ASC', 'PRICE_DESC'] as const;
export type SortType = (typeof SORT_TYPES)[number];

/** The search page state; it lives in the URL (see utils/searchQuery). */
export type SearchParams = {
  keyword: string;
  page: number;
  brand: string[];
  category: string[];
  attribute: string[];
  minPrice?: number;
  maxPrice?: number;
  sortType: SortType;
};
