import { get, post } from '../../../common/services/ApiClientService';
import { CancelResult, CaptureResult, InitPaymentResult, PaymentProvider } from '../models/Payment';

export type { CaptureResult } from '../models/Payment';

const base = '/payment';

export const getPaymentProviders = () => get<PaymentProvider[]>(`${base}/storefront/payment-providers`);

/** Only the order id and the method: the payment service reads the amount from the order (decisions.md 5-12). */
export const initPayment = (orderId: number, paymentMethod: string) =>
  post<InitPaymentResult>(`${base}/init`, { orderId, paymentMethod });

/** PayPal's return: the server captures and verifies the payment; the browser never says "paid". */
export const capturePayment = (token: string) => post<CaptureResult>(`${base}/capture`, { token });

/** PayPal's cancel return: the buyer's pending PayPal payment is cancelled — a POST, never a GET (decisions.md 5-68). */
export const cancelPayment = (token: string) => post<CancelResult>(`${base}/cancel`, { token });
