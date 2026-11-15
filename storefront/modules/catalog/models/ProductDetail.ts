import { ProductAttributeGroup } from './ProductAttribute';

export type ProductDetail = {
  id: number;
  name: string;
  brandName: string | null;
  productCategories: string[];
  productAttributeGroups: ProductAttributeGroup[];
  shortDescription: string | null;
  description: string | null;
  specification: string | null;
  isAllowedToOrder: boolean;
  isPublished: boolean;
  isFeatured: boolean;
  hasOptions: boolean;
  price: number;
  /** decisions.md 3c-24: false when tracked and none left; optional for older product services */
  stockTrackingEnabled?: boolean;
  stockAvailable?: boolean;
  stockQuantity?: number | null;
  thumbnailMediaUrl: string;
  productImageMediaUrls: string[];
};
