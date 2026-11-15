import { PAGE_SIZE } from '../../../common/models/Page';
import { get, put } from '../../../common/services/ApiClientService';
import { Payment, PaymentFilter, PaymentPage, PaymentProvider } from '../models/Payment';

const base = '/payment/backoffice';

export const getPaymentProviders = () => get<PaymentProvider[]>(`${base}/payment-providers`);

/** YAS PUT /backoffice/payment-providers: the id travels in the body. */
export const updatePaymentProvider = (provider: PaymentProvider) =>
  put<PaymentProvider>(`${base}/payment-providers`, {
    id: provider.id,
    enabled: provider.enabled,
    name: provider.name,
    configureUrl: provider.configureUrl,
    landingViewComponentName: provider.landingViewComponentName,
    additionalSettings: provider.additionalSettings,
    mediaId: provider.mediaId,
  });

export function getPayments(pageNo: number, filter: PaymentFilter) {
  const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(PAGE_SIZE) });
  if (filter.orderId.trim()) {
    params.set('orderId', filter.orderId.trim());
  }
  if (filter.paymentStatus) {
    params.set('paymentStatus', filter.paymentStatus);
  }
  if (filter.paymentMethod) {
    params.set('paymentMethod', filter.paymentMethod);
  }
  return get<PaymentPage>(`${base}/payments?${params}`);
}

/** Cash collected on delivery: the payment service marks the order paid with the recorded amount. */
export const confirmCodPayment = (id: number) => put<Payment>(`${base}/payments/${id}/confirm`, {});
