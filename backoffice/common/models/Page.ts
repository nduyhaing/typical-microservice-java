/** The paging fields every YAS `...ListGetVm` shares (the content list has a service-specific name). */
export type PageInfo = {
  pageNo: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
};

/** Page size of the back office list pages. */
export const PAGE_SIZE = 10;
