import { PAGE_SIZE } from '../../../common/models/Page';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { PromotionDetail, PromotionFilter, PromotionPage, PromotionPayload } from '../models/Promotion';

const base = '/promotion/backoffice/promotions';

type PromotionListVm = Omit<PromotionPage, 'isLast'>;

/** A page of promotions matching the filter (YAS `GET /backoffice/promotions`, newest first). */
export async function getPromotions(pageNo: number, filter: PromotionFilter): Promise<PromotionPage> {
  const params = [
    `pageNo=${pageNo}`,
    `pageSize=${PAGE_SIZE}`,
    `promotionName=${encodeURIComponent(filter.promotionName)}`,
    `couponCode=${encodeURIComponent(filter.couponCode)}`,
  ];
  if (filter.startDate) {
    params.push(`startDate=${encodeURIComponent(filter.startDate)}`);
  }
  if (filter.endDate) {
    params.push(`endDate=${encodeURIComponent(filter.endDate)}`);
  }
  const page = await get<PromotionListVm>(`${base}?${params.join('&')}`);
  return { ...page, isLast: page.pageNo + 1 >= page.totalPages };
}

export const getPromotion = (id: number) => get<PromotionDetail>(`${base}/${id}`);

export const createPromotion = (promotion: PromotionPayload) => post<PromotionDetail>(base, promotion);

/** YAS puts the id in the body: `PUT /backoffice/promotions`. */
export const updatePromotion = (id: number, promotion: PromotionPayload) =>
  put<PromotionDetail>(base, { id, ...promotion });

export const deletePromotion = (id: number) => del(`${base}/${id}`);
