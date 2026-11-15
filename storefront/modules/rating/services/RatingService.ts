import { get, post } from '../../../common/services/ApiClientService';
import { Rating, RatingList, RatingPost } from '../models/Rating';

const base = '/rating/storefront/ratings';

/** Reviews shown per page on the product page. */
export const REVIEWS_PAGE_SIZE = 5;

export const getRatingsByProduct = (productId: number, pageNo: number, pageSize: number) =>
  get<RatingList>(`${base}/products/${productId}?pageNo=${pageNo}&pageSize=${pageSize}`);

export const getAverageStar = (productId: number) => get<number>(`${base}/product/${productId}/average-star`);

/** Only customers who bought the product may post, once per product (the server checks). */
export const createRating = (rating: RatingPost) => post<Rating>(base, rating);
