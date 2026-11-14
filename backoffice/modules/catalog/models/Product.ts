/** ImageVm (YAS). */
export type ImageVm = { id: number; url: string };

/** ProductListVm (YAS). */
export type ProductListItem = {
  id: number;
  name: string;
  slug: string;
  isAllowedToOrder: boolean;
  isPublished: boolean;
  isFeatured: boolean;
  isVisibleIndividually: boolean;
  price: number | null;
  createdOn: string | null;
  taxClassId: number | null;
  parentId: number | null;
};

/** ProductListGetVm (YAS). */
export type ProductListGet = {
  productContent: ProductListItem[];
  pageNo: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
};

/** ProductDetailVm (YAS); `categories` are the category entities (only `id` and `name` are used). */
export type ProductDetail = {
  id: number;
  name: string;
  shortDescription: string | null;
  description: string | null;
  specification: string | null;
  sku: string | null;
  gtin: string | null;
  slug: string;
  isAllowedToOrder: boolean | null;
  isPublished: boolean | null;
  isFeatured: boolean | null;
  isVisible: boolean | null;
  stockTrackingEnabled: boolean | null;
  weight: number | null;
  dimensionUnit: string | null;
  length: number | null;
  width: number | null;
  height: number | null;
  price: number | null;
  brandId: number | null;
  categories: { id: number; name: string }[] | null;
  metaTitle: string | null;
  metaKeyword: string | null;
  metaDescription: string | null;
  thumbnailMedia: ImageVm | null;
  productImageMedias: ImageVm[] | null;
  taxClassId: number | null;
  parentId: number | null;
};

/** ProductVariationGetVm (YAS): `options` maps option id to the variation's value. */
export type ProductVariationGet = {
  id: number;
  name: string;
  slug: string;
  sku: string | null;
  gtin: string | null;
  price: number | null;
  thumbnail: ImageVm | null;
  productImages: ImageVm[] | null;
  options: Record<string, string>;
};

/** ProductOptionGetVm (YAS). */
export type ProductOption = {
  id: number;
  name: string;
};

/** ProductOptionValuePostVm / ProductOptionValuePutVm (YAS). */
export type ProductOptionValuePost = {
  productOptionId: number;
  displayType: string;
  displayOrder: number;
  value: string[];
};

/** ProductOptionValueDisplay (YAS): `value` is a JSON object string, value -> display colour. */
export type ProductOptionValueDisplay = {
  productOptionId: number;
  displayType: string;
  displayOrder: number;
  value: string;
};

/** ProductVariationPostVm (YAS); ProductVariationPutVm adds `id` (null for a new variation). */
export type ProductVariationPost = {
  id?: number | null;
  name: string;
  slug: string;
  sku: string;
  gtin: string;
  price: number;
  thumbnailMediaId: number | null;
  productImageIds: number[];
  optionValuesByOptionId: Record<number, string>;
};

/** ProductPostVm / ProductPutVm (YAS): create and update body. */
export type ProductPayload = {
  name: string;
  slug: string;
  brandId: number | null;
  categoryIds: number[];
  shortDescription: string;
  description: string;
  specification: string;
  sku: string;
  gtin: string;
  weight: number;
  dimensionUnit: string;
  length: number;
  width: number;
  height: number;
  price: number;
  isAllowedToOrder: boolean;
  isPublished: boolean;
  isFeatured: boolean;
  isVisibleIndividually: boolean;
  stockTrackingEnabled: boolean;
  metaTitle: string;
  metaKeyword: string;
  metaDescription: string;
  thumbnailMediaId: number | null;
  productImageIds: number[];
  variations: ProductVariationPost[];
  productOptionValues: ProductOptionValuePost[];
  productOptionValueDisplays: ProductOptionValueDisplay[];
  relatedProductIds: number[];
  taxClassId: number | null;
};
