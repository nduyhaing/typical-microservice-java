import { get, post } from '../../../common/services/ApiClientService';
import { Checkout } from '../models/Checkout';
import { AddressChoice, Order, OrderPost, OrderPreview } from '../models/Order';

const base = '/order/storefront';

export const createCheckout = (items: { productId: number; quantity: number }[]) =>
  post<Checkout>(`${base}/checkouts`, { checkoutItemPostVms: items });

export const getCheckout = (id: string) => get<Checkout>(`${base}/checkouts/${encodeURIComponent(id)}`);

export const placeOrder = (order: OrderPost) => post<Order>(`${base}/orders`, order);

/** Subtotal, tax and total for the checkout shipped to `address`, computed by the order service. */
export const previewOrder = (checkoutId: string, address: AddressChoice) =>
  post<OrderPreview>(`${base}/orders/preview`, { checkoutId, ...address });

export const getMyOrders = () => get<Order[]>(`${base}/orders/my-orders`);
