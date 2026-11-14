import { del, get, post, put } from '../../../common/services/ApiClientService';
import { getProductsByIds } from '../../catalog/services/ProductService';
import { CartItem, CartLine } from '../models/CartItem';

const base = '/cart/storefront/cart/items';

export const getCartItems = () => get<CartItem[]>(base);

export async function getCartItemCount(): Promise<number> {
  const items = await getCartItems();
  return items.reduce((total, item) => total + item.quantity, 0);
}

export const UNAVAILABLE_PRODUCT_NAME = 'Product no longer available';

/**
 * Cart items with product details. Items whose product is no longer available are kept as unavailable lines, so
 * the page matches the header count and the customer can remove them.
 */
export async function getCartLines(): Promise<CartLine[]> {
  const items = await getCartItems();
  if (items.length === 0) {
    return [];
  }
  const products = await getProductsByIds(items.map((item) => item.productId));
  return items.map((item) => {
    const product = products.find((candidate) => candidate.id === item.productId);
    if (!product) {
      return {
        productId: item.productId,
        name: UNAVAILABLE_PRODUCT_NAME,
        slug: '',
        thumbnailUrl: '',
        price: 0,
        quantity: item.quantity,
        available: false,
      };
    }
    return {
      productId: item.productId,
      name: product.name,
      slug: product.slug,
      thumbnailUrl: product.thumbnailUrl,
      price: product.price ?? 0,
      quantity: item.quantity,
      available: true,
    };
  });
}

export async function addToCart(productId: number, quantity: number): Promise<void> {
  await post(base, { productId, quantity });
}

export async function updateQuantity(productId: number, quantity: number): Promise<void> {
  await put(`${base}/${productId}`, { quantity });
}

export async function removeItem(productId: number): Promise<void> {
  await del(`${base}/${productId}`);
}
