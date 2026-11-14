import { get, post } from '../../../common/services/ApiClientService';
import { Checkout } from '../models/Checkout';
import { Order, OrderPost } from '../models/Order';

const base = '/order/storefront';

export const createCheckout = (items: { productId: number; quantity: number }[]) =>
  post<Checkout>(`${base}/checkouts`, { checkoutItemPostVms: items });

export const getCheckout = (id: string) => get<Checkout>(`${base}/checkouts/${encodeURIComponent(id)}`);

export const placeOrder = (order: OrderPost) => post<Order>(`${base}/orders`, order);

export const getMyOrders = () => get<Order[]>(`${base}/orders/my-orders`);
