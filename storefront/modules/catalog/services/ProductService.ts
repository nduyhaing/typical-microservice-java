import { get } from '../../../common/services/ApiClientService';
import { ProductDetail } from '../models/ProductDetail';
import { ProductFeature } from '../models/ProductFeature';
import { ProductOptionValueGet } from '../models/ProductOptionValueGet';
import { ProductsGet } from '../models/ProductsGet';
import { ProductThumbnail } from '../models/ProductThumbnail';
import { ProductVariation } from '../models/ProductVariation';

const base = '/product/storefront';

export const getFeaturedProducts = (pageNo: number) =>
  get<ProductFeature>(`${base}/products/featured?pageNo=${pageNo}`);

/** query: e.g. `categorySlug=phones&pageNo=0` (YAS /storefront/products parameters) */
export const getProductByMultiParams = (query: string) => get<ProductsGet>(`${base}/products?${query}`);

export const getProductsByBrand = (brandSlug: string) =>
  get<ProductThumbnail[]>(`${base}/brand/${encodeURIComponent(brandSlug)}/products`);

export const getProductDetail = (slug: string) =>
  get<ProductDetail>(`${base}/product/${encodeURIComponent(slug)}`);

export const getProductVariationsByParentId = (parentId: number) =>
  get<ProductVariation[]>(`${base}/product-variations/${parentId}`);

export const getProductOptionValues = (productId: number) =>
  get<ProductOptionValueGet[]>(`${base}/product-option-combinations/${productId}/values`);

export const getRelatedProductsByProductId = (productId: number) =>
  get<ProductsGet>(`${base}/products/related-products/${productId}`);
