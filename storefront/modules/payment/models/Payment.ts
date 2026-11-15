/** A payment provider a customer can pay with right now (GET /payment/storefront/payment-providers). */
export type PaymentProvider = {
  id: string;
  name: string;
  configureUrl?: string;
  version: number;
  mediaId?: number;
  iconUrl?: string;
};

/** COD answers PENDING and no redirect; PayPal answers the approval URL to send the buyer to. */
export type InitPaymentResult = {
  status: string;
  paymentId: number;
  redirectUrl: string | null;
};

export type CaptureResult = {
  orderId: number;
  checkoutId?: string;
  paymentId: number;
  amount?: number;
  paymentFee?: number;
  gatewayTransactionId?: string;
  paymentMethod?: string;
  paymentStatus: string;
  failureMessage?: string | null;
};

export type CancelResult = { paymentId: number; orderId: number; paymentStatus: string };

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export function paymentStatusLabel(status: string): string {
  return PAYMENT_STATUS_LABELS[status] ?? status;
}
