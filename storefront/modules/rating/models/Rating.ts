/** A product review (rating service RatingVm). */
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

export type RatingList = {
  ratingList: Rating[];
  totalElements: number;
  totalPages: number;
};

/** The product name is taken by the server from the product service. */
export type RatingPost = {
  productId: number;
  star: number;
  content: string;
};

/** What the product page loads on the server: the average and the first page of reviews. */
export type ProductReviewsData = {
  average: number;
  ratings: RatingList;
};
