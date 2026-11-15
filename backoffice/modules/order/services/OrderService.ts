import { PAGE_SIZE } from '../../../common/models/Page';
import { get, put } from '../../../common/services/ApiClientService';
import { OrderBrief, OrderDetail, OrderFilter, OrderPage, OrderStatusChange } from '../models/Order';

const base = '/order/backoffice/orders';

type OrderListVm = { orderList: OrderBrief[]; totalElements: number; totalPages: number };

/** Start (00:00:00.000) or end (23:59:59.999) of a `yyyy-mm-dd` day in the browser's time zone, as an ISO instant. */
function dayBoundary(day: string, end: boolean): string {
  const [year, month, date] = day.split('-').map(Number);
  return (end ? new Date(year, month - 1, date, 23, 59, 59, 999) : new Date(year, month - 1, date)).toISOString();
}

/** A page of orders matching the filter (YAS `GET /backoffice/orders`, newest first). */
export async function getOrders(pageNo: number, filter: OrderFilter): Promise<OrderPage> {
  const params = new URLSearchParams({
    pageNo: String(pageNo),
    pageSize: String(PAGE_SIZE),
    email: filter.email,
    productName: filter.productName,
  });
  if (filter.orderStatus.length > 0) {
    params.set('orderStatus', filter.orderStatus.join(','));
  }
  if (filter.createdFrom) {
    params.set('createdFrom', dayBoundary(filter.createdFrom, false));
  }
  if (filter.createdTo) {
    params.set('createdTo', dayBoundary(filter.createdTo, true));
  }
  const page = await get<OrderListVm>(`${base}?${params.toString()}`);
  return { ...page, pageNo, pageSize: PAGE_SIZE, isLast: pageNo + 1 >= page.totalPages };
}

export const getOrder = (id: number) => get<OrderDetail>(`${base}/${id}`);

/** Moves the order (only to one of its `allowedStatuses`; the server refuses others with 400). */
export const updateOrderStatus = (id: number, change: OrderStatusChange) => put<OrderDetail>(`${base}/${id}/status`, change);
