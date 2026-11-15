import { ApiError } from '../../../common/services/ApiClientService';

/** Promotion's refusal codes (ErrorVm `title`, decisions.md 4-43) in the customer's words. */
const COUPON_MESSAGES: Record<string, string> = {
  PROMOTION_NOT_FOUND: 'This coupon code does not exist.',
  PROMOTION_NOT_STARTED: 'This coupon is not valid yet.',
  PROMOTION_EXPIRED: 'This coupon has expired.',
  PROMOTION_USED_UP: 'This coupon has been used up.',
  MINIMUM_ORDER_NOT_REACHED: 'Your order does not reach the minimum amount for this coupon.',
  PROMOTION_NOT_APPLICABLE: 'This coupon does not apply to the products in your order.',
  USAGE_REFERENCE_CONFLICT: 'This coupon could not be used. Please try again.',
};

/** The friendly text of a refused coupon (a 400 whose title is one of promotion's codes), else undefined. */
export function couponErrorMessage(error: unknown): string | undefined {
  if (!(error instanceof ApiError) || error.status !== 400) {
    return undefined;
  }
  const title = (error.body as { title?: unknown } | null)?.title;
  return typeof title === 'string' ? COUPON_MESSAGES[title] : undefined;
}
