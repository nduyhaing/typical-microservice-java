/** Recommendation `GET /embedding/product/{id}/similarity` item (RelatedProductVm, decisions.md 5-43). */
export type SimilarProduct = {
  id: number;
  name: string;
  slug: string;
  price: number | null;
  brandName: string | null;
  thumbnailUrl: string | null;
};
