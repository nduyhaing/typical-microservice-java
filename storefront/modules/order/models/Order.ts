export type OrderAddress = {
  contactName: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  zipCode?: string;
  countryName: string;
};

export type OrderItem = {
  productId: number;
  productName: string;
  quantity: number;
  productPrice: number;
};

export type Order = {
  id: number;
  email: string | null;
  checkoutId: string;
  note: string | null;
  numberItem: number;
  totalPrice: number;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  createdOn: string;
  orderItemVms: OrderItem[];
};

/** What the browser sends to place an order: no prices or totals (decisions.md 2c-1). */
export type OrderPost = {
  checkoutId: string;
  shippingAddressPostVm: OrderAddress;
  note?: string;
  paymentMethod: 'COD';
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
