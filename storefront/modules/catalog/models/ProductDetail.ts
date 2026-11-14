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
  thumbnailMediaUrl: string;
  productImageMediaUrls: string[];
};
