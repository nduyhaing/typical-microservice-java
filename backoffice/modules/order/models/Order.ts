import { PageInfo } from '../../../common/models/Page';

/** YAS order statuses; which change is allowed is decided by the order service (`allowedStatuses`). */
export type OrderStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'SHIPPING'
  | 'COMPLETED'
  | 'REFUND'
  | 'CANCELLED'
  | 'REJECT';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';
export type DeliveryStatus = 'PREPARING' | 'DELIVERING' | 'DELIVERED' | 'CANCELLED';
export type DeliveryMethod = 'VIETTEL_POST' | 'GRAB_EXPRESS' | 'SHOPEE_EXPRESS' | 'YAS_EXPRESS';

export const ORDER_STATUSES: OrderStatus[] = [
  'PENDING',
  'PENDING_PAYMENT',
  'ACCEPTED',
  'PAID',
  'SHIPPING',
  'COMPLETED',
  'CANCELLED',
  'REJECT',
  'REFUND',
];
export const DELIVERY_METHODS: DeliveryMethod[] = ['VIETTEL_POST', 'GRAB_EXPRESS', 'SHOPEE_EXPRESS', 'YAS_EXPRESS'];

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
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

/** The button that moves an order to the status. */
export const STATUS_ACTIONS: Record<OrderStatus, string> = {
  PENDING: 'Back to pending',
  ACCEPTED: 'Accept',
  PENDING_PAYMENT: 'Await payment',
  PAID: 'Mark as paid',
  SHIPPING: 'Ship',
  COMPLETED: 'Complete',
  REFUND: 'Refund',
  CANCELLED: 'Cancel order',
  REJECT: 'Reject',
};

/** Changes that end the order: asked to confirm first. */
export const CONFIRMED_STATUSES: OrderStatus[] = ['CANCELLED', 'REJECT', 'REFUND'];

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Pending',
  COMPLETED: 'Paid',
  CANCELLED: 'Cancelled',
};

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  PREPARING: 'Preparing',
  DELIVERING: 'Delivering',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

export const DELIVERY_METHOD_LABELS: Record<DeliveryMethod, string> = {
  VIETTEL_POST: 'Viettel Post',
  GRAB_EXPRESS: 'Grab Express',
  SHOPEE_EXPRESS: 'Shopee Express',
  YAS_EXPRESS: 'YAS Express',
};

export type OrderAddress = {
  contactName: string | null;
  phone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  zipCode: string | null;
  districtName: string | null;
  stateOrProvinceName: string | null;
  countryName: string | null;
};

/** A row of the order list (YAS OrderBriefVm). */
export type OrderBrief = {
  id: number;
  email: string;
  billingAddressVm: OrderAddress | null;
  totalPrice: number;
  orderStatus: OrderStatus;
  deliveryMethod: DeliveryMethod | null;
  deliveryStatus: DeliveryStatus | null;
  paymentStatus: PaymentStatus | null;
  createdOn: string;
};

export type OrderPage = PageInfo & { orderList: OrderBrief[] };

export type OrderItem = {
  productId: number;
  productName: string;
  quantity: number;
  productPrice: number;
  discountAmount: number;
  taxPercent: number | null;
  taxAmount: number | null;
};

/** Stock and coupon use of an order that ended before shipping (null while it keeps them). */
export type GoodsBack = {
  requestedOn: string;
  stockToRestore: boolean;
  stockRestoredOn: string | null;
  couponToRelease: boolean;
  couponReleasedOn: string | null;
};

/** An order as the back office shows it (order service OrderDetailVm). */
export type OrderDetail = {
  id: number;
  email: string;
  customerId: string;
  checkoutId: string;
  note: string | null;
  numberItem: number;
  subtotal: number;
  tax: number | null;
  discount: number | null;
  totalPrice: number;
  deliveryFee: number | null;
  couponCode: string | null;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus | null;
  paymentMethod: string | null;
  paymentId: number | null;
  deliveryStatus: DeliveryStatus | null;
  deliveryMethod: DeliveryMethod | null;
  rejectReason: string | null;
  createdOn: string;
  lastModifiedOn: string | null;
  lastModifiedBy: string | null;
  shippingAddressVm: OrderAddress | null;
  billingAddressVm: OrderAddress | null;
  orderItemVms: OrderItem[];
  allowedStatuses: OrderStatus[];
  goodsBack: GoodsBack | null;
};

/** Order list filters; dates are `yyyy-mm-dd` from date inputs, '' for none. */
export type OrderFilter = {
  email: string;
  productName: string;
  orderStatus: OrderStatus[];
  createdFrom: string;
  createdTo: string;
};

export type OrderStatusChange = {
  orderStatus: OrderStatus;
  deliveryStatus?: DeliveryStatus;
  deliveryMethod?: DeliveryMethod;
  rejectReason?: string;
};

/** Address lines joined for display. */
export function addressLines(address: OrderAddress | null): string[] {
  if (!address) {
    return [];
  }
  const place = [address.districtName, address.city, address.stateOrProvinceName, address.zipCode].filter(Boolean).join(', ');
  return [address.contactName, address.phone, address.addressLine1, address.addressLine2, place, address.countryName].filter(
    (line): line is string => Boolean(line && line.trim())
  );
}
