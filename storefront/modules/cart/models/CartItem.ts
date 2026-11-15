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
  /** false when the product was unpublished or removed after it was added: shown, not priced, removable */
  available: boolean;
  /** units in stock when the product's stock is tracked (decisions.md 3c-24); undefined = no limit */
  stockQuantity?: number;
};
