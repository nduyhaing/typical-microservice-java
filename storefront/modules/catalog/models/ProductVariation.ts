import { Media } from './Media';

export type ProductVariation = {
  id: number;
  name: string;
  slug: string;
  sku: string;
  gtin: string;
  price: number;
  thumbnail: Media | null;
  productImages: Media[];
  /** option id -> value */
  options: Record<number, string>;
};
