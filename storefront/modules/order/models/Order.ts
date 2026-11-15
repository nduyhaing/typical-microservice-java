import { AddressPayload } from '../../address/models/Address';

/** A new address typed in at checkout: location ids, the server fills in the names (decisions.md 3c-41). */
export type OrderAddress = AddressPayload;

/** Where to ship: one of the customer's saved addresses (location address id) or a new address. */
export type AddressChoice = { shippingAddressId: number } | { shippingAddressPostVm: OrderAddress };

export type OrderItem = {
  productId: number;
  productName: string;
  quantity: number;
  productPrice: number;
  discountAmount?: number;
  taxPercent?: number;
  taxAmount?: number;
};

export type Order = {
  id: number;
  email: string | null;
  checkoutId: string;
  note: string | null;
  numberItem: number;
  subtotal?: number;
  tax?: number;
  discount?: number;
  couponCode?: string | null;
  /** subtotal - discount + tax, computed by the server */
  totalPrice: number;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  createdOn: string;
  orderItemVms: OrderItem[];
  /** Whether the customer may still cancel it (pending / pending payment), decided by the order service. */
  cancellable?: boolean;
};

/** What the browser sends to place an order: no prices, tax or totals (decisions.md 2c-1). */
export type OrderPost = AddressChoice & {
  checkoutId: string;
  note?: string;
  paymentMethod: 'COD';
  /** a coupon the customer applied; the server verifies it and computes the discount (decisions.md 4-50) */
  couponCode?: string;
};

export type OrderPreviewItem = {
  productId: number;
  productName: string;
  quantity: number;
  productPrice: number;
  subtotal: number;
  discountAmount?: number;
  taxPercent: number;
  taxAmount: number;
};

/** The server's price of a checkout for an address (POST /storefront/orders/preview); the browser never sums tax. */
export type OrderPreview = {
  checkoutId: string;
  subtotal: number;
  totalTax: number;
  discount: number;
  deliveryFee: number;
  total: number;
  /** the coupon as promotion knows it, when one was applied */
  couponCode?: string | null;
  items: OrderPreviewItem[];
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  PENDING_PAYMENT: 'Pending payment',
  PAID: 'Paid',
  SHIPPING: 'Shipping',
  COMPLETED: 'Completed',
  REFUND: 'Refunded',
  CANCELLED: 'Cancelled',
  REJECT: 'Rejected',
};

export function orderStatusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}
