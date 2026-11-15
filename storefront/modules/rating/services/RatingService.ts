import { get, post } from '../../../common/services/ApiClientService';
import { Rating, RatingList, RatingPost } from '../models/Rating';

const base = '/rating/storefront/ratings';

/** Reviews shown per page on the product page. */
export const REVIEWS_PAGE_SIZE = 5;

/**
 * How long a read waits for the rating service: the product page is server-rendered, so a hanging rating service
 * must not hang the page; on timeout the reviews section says "not available" (decisions.md 4-78).
 */
export const RATING_TIMEOUT_MS = 1500;

export const getRatingsByProduct = (
  productId: number,
  pageNo: number,
  pageSize: number,
  signal: AbortSignal = AbortSignal.timeout(RATING_TIMEOUT_MS)
) => get<RatingList>(`${base}/products/${productId}?pageNo=${pageNo}&pageSize=${pageSize}`, signal);

export const getAverageStar = (productId: number, signal: AbortSignal = AbortSignal.timeout(RATING_TIMEOUT_MS)) =>
  get<number>(`${base}/product/${productId}/average-star`, signal);

/** Only customers who bought the product may post, once per product (the server checks). */
export const createRating = (rating: RatingPost) => post<Rating>(base, rating);
