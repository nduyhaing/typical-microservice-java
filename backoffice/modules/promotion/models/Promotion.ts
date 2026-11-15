import { PageInfo } from '../../../common/models/Page';

export type DiscountType = 'PERCENTAGE' | 'FIXED';
export type ApplyTo = 'PRODUCT' | 'BRAND' | 'CATEGORY';
export type UsageType = 'LIMITED' | 'UNLIMITED';

/** The editable fields (PromotionPostVm; PromotionPutVm adds `id`). Dates are ISO-8601 instants. */
export type PromotionPayload = {
  name: string;
  slug: string;
  description: string | null;
  couponCode: string;
  discountType: DiscountType;
  discountPercentage: number | null;
  discountAmount: number | null;
  usageType: UsageType;
  usageLimit: number | null;
  minimumOrderPurchaseAmount: number;
  isActive: boolean;
  startDate: string;
  endDate: string;
  applyTo: ApplyTo;
  productIds: number[];
  brandIds: number[];
  categoryIds: number[];
};

/** PromotionDetailVm: targets as ids (4-44). */
export type PromotionDetail = PromotionPayload & {
  id: number;
  usageCount: number;
};

/** PromotionListVm (YAS) has no `isLast`; the service adds it for the pager. */
export type PromotionPage = PageInfo & { promotionDetailVmList: PromotionDetail[] };

export type PromotionFilter = {
  promotionName: string;
  couponCode: string;
  /** ISO instants or empty. */
  startDate: string;
  endDate: string;
};
