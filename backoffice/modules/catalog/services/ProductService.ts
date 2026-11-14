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
 * Ids of the product's related products, so an update keeps them (the form does not edit them yet). YAS's back office
 * path first; this API may only have the storefront one, which lists published related products of a published
 * product.
 */
export async function getRelatedProductIds(id: number): Promise<number[]> {
  try {
    const related = await get<{ id: number }[]>(`${base}/products/related-products/${id}`);
    return related.map((p) => p.id);
  } catch {
    try {
      const page = await get<{ productContent: { id: number }[] }>(
        `/product/storefront/products/related-products/${id}?pageNo=0&pageSize=100`
      );
      return page.productContent.map((p) => p.id);
    } catch {
      return [];
    }
  }
}
