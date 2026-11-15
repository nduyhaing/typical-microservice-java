import { del, get, post, put } from '../../../common/services/ApiClientService';
import { ProductDetail, ProductListGet, ProductPayload, ProductVariationGet } from '../models/Product';

const base = '/product/backoffice';

export const PRODUCT_PAGE_SIZE = 10;

export const getProducts = (pageNo: number, productName: string) =>
  get<ProductListGet>(
    `${base}/products?pageNo=${pageNo}&pageSize=${PRODUCT_PAGE_SIZE}&product-name=${encodeURIComponent(productName)}`
  );

export const getProduct = (id: number) => get<ProductDetail>(`${base}/products/${id}`);

export const getProductVariations = (id: number) => get<ProductVariationGet[]>(`${base}/product-variations/${id}`);

export const createProduct = (product: ProductPayload) => post<unknown>(`${base}/products`, product);

export const updateProduct = (id: number, product: ProductPayload) => put<null>(`${base}/products/${id}`, product);

export const deleteProduct = (id: number) => del(`${base}/products/${id}`);

/**
 * Ids of all the product's related products (published or not), so an update keeps them: the form does not edit them
 * yet and an update replaces the list (3a-19, 3a-35). A failure rejects, so the edit page cannot be saved.
 */
export async function getRelatedProductIds(id: number): Promise<number[]> {
  const related = await get<{ id: number }[]>(`${base}/products/related-products/${id}`);
  return related.map((p) => p.id);
}
