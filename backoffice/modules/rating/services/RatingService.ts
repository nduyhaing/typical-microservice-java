import { PAGE_SIZE } from '../../../common/models/Page';
import { del, get } from '../../../common/services/ApiClientService';
import { Rating, RatingFilter, RatingPage } from '../models/Rating';

const base = '/rating/backoffice/ratings';

type RatingListVm = { ratingList: Rating[]; totalElements: number; totalPages: number };

/** Start (00:00:00.000) or end (23:59:59.999) of a `yyyy-mm-dd` day in the browser's time zone, as an ISO instant. */
function dayBoundary(day: string, end: boolean): string {
  const [year, month, date] = day.split('-').map(Number);
  return (end ? new Date(year, month - 1, date, 23, 59, 59, 999) : new Date(year, month - 1, date)).toISOString();
}

/** A page of reviews matching the filter (YAS `GET /backoffice/ratings`, newest first). */
export async function getRatings(pageNo: number, filter: RatingFilter): Promise<RatingPage> {
  const params = new URLSearchParams({
    pageNo: String(pageNo),
    pageSize: String(PAGE_SIZE),
    productName: filter.productName,
    customerName: filter.customerName,
    message: filter.message,
  });
  if (filter.createdFrom) {
    params.set('createdFrom', dayBoundary(filter.createdFrom, false));
  }
  if (filter.createdTo) {
    params.set('createdTo', dayBoundary(filter.createdTo, true));
  }
  const page = await get<RatingListVm>(`${base}?${params.toString()}`);
  return { ...page, pageNo, pageSize: PAGE_SIZE, isLast: pageNo + 1 >= page.totalPages };
}

export const getLatestRatings = (count: number) => get<Rating[]>(`${base}/latest/${count}`);

export const deleteRating = (id: number) => del(`${base}/${id}`);
