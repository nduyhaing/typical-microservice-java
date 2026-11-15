import { get, post } from '../../../common/services/ApiClientService';
import { Checkout } from '../models/Checkout';
import { AddressChoice, Order, OrderPost, OrderPreview } from '../models/Order';

const base = '/order/storefront';

export const createCheckout = (items: { productId: number; quantity: number }[]) =>
  post<Checkout>(`${base}/checkouts`, { checkoutItemPostVms: items });

export const getCheckout = (id: string) => get<Checkout>(`${base}/checkouts/${encodeURIComponent(id)}`);

export const placeOrder = (order: OrderPost) => post<Order>(`${base}/orders`, order);

/**
 * Subtotal, discount, tax and total for the checkout shipped to `address`, computed by the order service; with a
 * coupon code the server verifies it with promotion (400 with promotion's code in `title` when refused).
 */
export const previewOrder = (checkoutId: string, address: AddressChoice, couponCode?: string) =>
  post<OrderPreview>(`${base}/orders/preview`, { checkoutId, ...address, ...(couponCode ? { couponCode } : {}) });

export const getMyOrders = () => get<Order[]>(`${base}/orders/my-orders`);

/** The customer cancels their own order while it is pending or awaiting payment (400 with the reason otherwise). */
export const cancelOrder = (id: number) => post<Order>(`${base}/orders/${id}/cancel`, {});
