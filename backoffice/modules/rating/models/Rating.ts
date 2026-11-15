import { PageInfo } from '../../../common/models/Page';

/** A customer's review of a product (rating service RatingVm). */
export type Rating = {
  id: number;
  content: string;
  star: number;
  productId: number;
  productName: string | null;
  createdBy: string;
  firstName: string | null;
  lastName: string | null;
  createdOn: string;
};

/** Reviews page filters (YAS RatingSearchForm); dates are `yyyy-mm-dd` from date inputs, '' for none. */
export type RatingFilter = {
  productName: string;
  customerName: string;
  message: string;
  createdFrom: string;
  createdTo: string;
};

export type RatingPage = PageInfo & { ratingList: Rating[] };

export function customerName(rating: Rating): string {
  return [rating.firstName, rating.lastName].filter((part) => part && part.trim()).join(' ') || rating.createdBy;
}
