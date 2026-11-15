import { PageInfo } from '../../../common/models/Page';

/** A payment provider as the back office sees it (YAS backoffice modules/payment/models/Payment.ts, reworked). */
export type PaymentProvider = {
  id: string;
  name: string;
  /** the admin's switch */
  enabled: boolean;
  /** offered to customers right now: enabled, handled by the payment service, PayPal credentials configured */
  available: boolean;
  configureUrl: string | null;
  landingViewComponentName: string | null;
  additionalSettings: string | null;
  mediaId: number | null;
  version: number;
};

export type PaymentMethod = 'COD' | 'BANKING' | 'PAYPAL';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';

export const PAYMENT_STATUSES: PaymentStatus[] = ['PENDING', 'COMPLETED', 'CANCELLED'];
export const PAYMENT_METHODS: PaymentMethod[] = ['COD', 'PAYPAL'];
export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Pending',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};
export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  COD: 'Cash on delivery',
  BANKING: 'Banking',
  PAYPAL: 'PayPal',
};

export type Payment = {
  id: number;
  orderId: number;
  checkoutId: string | null;
  customerId: string;
  amount: number;
  paymentFee: number | null;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  gatewayTransactionId: string | null;
  paymentProviderCheckoutId: string | null;
  failureMessage: string | null;
  /** the order service accepted the payment (order is PAID) */
  orderConfirmed: boolean;
  /** the order service refused the completed payment: refund it by hand (decisions.md 5-61) */
  orderRefused: boolean;
  /** money captured that the order did not accept (yet), or a capture that failed verification (5-63) */
  needsAttention: boolean;
  createdOn: string;
  lastModifiedOn: string;
};

export type PaymentPage = PageInfo & { payments: Payment[] };

export type PaymentFilter = {
  orderId: string;
  paymentStatus: PaymentStatus | '';
  paymentMethod: PaymentMethod | '';
  /** only payments that need a person (5-63) */
  needsAttention: boolean;
};
