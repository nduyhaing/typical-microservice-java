export type CheckoutItem = {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  productPrice: number;
};

/** A priced checkout from the order service (prices come from the server, never the browser). */
export type Checkout = {
  id: string;
  email: string | null;
  note: string | null;
  checkoutState: string;
  totalAmount: number;
  checkoutItemVms: CheckoutItem[];
};
