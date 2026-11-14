/** As stored by the cart service: ids and quantities only (YAS). */
export type CartItem = {
  customerId: string;
  productId: number;
  quantity: number;
};

/** A cart item joined with the product's name and price for display. */
export type CartLine = {
  productId: number;
  name: string;
  slug: string;
  thumbnailUrl: string;
  price: number;
  quantity: number;
};
