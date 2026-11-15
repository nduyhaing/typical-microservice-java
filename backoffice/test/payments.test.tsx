import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import * as api from '../common/services/ApiClientService';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import { Payment, PaymentProvider } from '../modules/payment/models/Payment';
import * as paymentService from '../modules/payment/services/PaymentService';
import PaymentProvidersPage from '../pages/sales/payment-providers';
import PaymentsPage from '../pages/sales/payments';

vi.mock('next/router', () => ({ useRouter: () => ({ push: vi.fn(), query: {}, isReady: true }) }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));

const provider = (id: string, patch: Partial<PaymentProvider> = {}): PaymentProvider => ({
  id,
  name: id === 'COD' ? 'Cash on Delivery' : 'PayPal',
  enabled: true,
  available: true,
  configureUrl: `${id.toLowerCase()}-config`,
  landingViewComponentName: null,
  additionalSettings: null,
  mediaId: null,
  version: 0,
  ...patch,
});

const payment = (id: number, patch: Partial<Payment> = {}): Payment => ({
  id,
  orderId: 10 + id,
  checkoutId: `c-${id}`,
  customerId: 'u-1',
  amount: 120.5,
  paymentFee: null,
  paymentMethod: 'COD',
  paymentStatus: 'PENDING',
  gatewayTransactionId: null,
  paymentProviderCheckoutId: null,
  failureMessage: null,
  orderConfirmed: false,
  createdOn: '2026-11-15T10:00:00Z',
  lastModifiedOn: '2026-11-15T10:00:00Z',
  ...patch,
});

const page = (list: Payment[], patch = {}) => ({
  payments: list,
  pageNo: 0,
  pageSize: 10,
  totalElements: list.length,
  totalPages: 1,
  isLast: true,
  ...patch,
});

beforeEach(() => {
  vi.mocked(authService.getAuthentication).mockResolvedValue({
    isAuthenticated: true,
    authenticatedUser: { username: 'admin' },
  });
});
afterEach(() => vi.restoreAllMocks());

describe('PaymentService', () => {
  it('calls the payment service paths', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValue([]);
    const put = vi.spyOn(api, 'put').mockResolvedValue({});

    await paymentService.getPaymentProviders();
    await paymentService.updatePaymentProvider(provider('COD', { name: 'Pay on delivery' }));
    await paymentService.getPayments(2, { orderId: '11', paymentStatus: 'PENDING', paymentMethod: '' });
    await paymentService.confirmCodPayment(5);

    expect(get).toHaveBeenCalledWith('/payment/backoffice/payment-providers');
    expect(put).toHaveBeenCalledWith('/payment/backoffice/payment-providers', expect.objectContaining({
      id: 'COD',
      name: 'Pay on delivery',
      enabled: true,
    }));
    const url = get.mock.calls[1][0];
    const params = new URLSearchParams(url.split('?')[1]);
    expect(url).toMatch(/^\/payment\/backoffice\/payments\?/);
    expect(params.get('pageNo')).toBe('2');
    expect(params.get('pageSize')).toBe('10');
    expect(params.get('orderId')).toBe('11');
    expect(params.get('paymentStatus')).toBe('PENDING');
    expect(params.has('paymentMethod')).toBe(false);
    expect(put).toHaveBeenCalledWith('/payment/backoffice/payments/5/confirm', {});
  });
});

describe('Payment providers page', () => {
  it('lists providers with enabled and offered-to-customers flags', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([
      provider('COD'),
      provider('PAYPAL', { available: false }),
    ]);

    render(<PaymentProvidersPage />);

    const paypal = (await screen.findByText('PayPal')).closest('tr') as HTMLElement;
    expect(within(paypal).getByText(/not configured/i)).toBeInTheDocument();
    const cod = screen.getByText('Cash on Delivery').closest('tr') as HTMLElement;
    expect(within(cod).getByRole('checkbox', { name: 'Enabled' })).toBeChecked();
  });

  it('edits the name and the enabled switch, then saves', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([provider('COD')]);
    const update = vi.spyOn(paymentService, 'updatePaymentProvider')
      .mockResolvedValue(provider('COD', { name: 'Pay on delivery', enabled: false, available: false }));

    render(<PaymentProvidersPage />);
    const row = (await screen.findByText('Cash on Delivery')).closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: 'Edit' }));
    const name = within(row).getByRole('textbox', { name: 'Name' });
    await userEvent.clear(name);
    await userEvent.type(name, 'Pay on delivery');
    await userEvent.click(within(row).getByRole('checkbox', { name: 'Enabled' }));
    await userEvent.click(within(row).getByRole('button', { name: 'Save' }));

    expect(update).toHaveBeenCalledWith(expect.objectContaining({ id: 'COD', name: 'Pay on delivery', enabled: false }));
    expect(await screen.findByText('Pay on delivery')).toBeInTheDocument();
  });

  it('shows the server error when saving fails', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([provider('COD')]);
    vi.spyOn(paymentService, 'updatePaymentProvider').mockRejectedValue(
      new ApiError(400, { detail: 'Request information is not valid', fieldErrors: ['name must not be blank'] })
    );

    render(<PaymentProvidersPage />);
    const row = (await screen.findByText('Cash on Delivery')).closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: 'Edit' }));
    await userEvent.click(within(row).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('name must not be blank')).toBeInTheDocument();
  });
});

describe('Payments page', () => {
  it('lists payments and filters them', async () => {
    const list = vi.spyOn(paymentService, 'getPayments').mockResolvedValue(page([
      payment(1),
      payment(2, { paymentMethod: 'PAYPAL', paymentStatus: 'COMPLETED', orderConfirmed: true, gatewayTransactionId: 'CAP1' }),
    ]));

    render(<PaymentsPage />);

    expect(await screen.findByText('#11')).toBeInTheDocument();
    expect(screen.getByText('CAP1')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'COMPLETED');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => expect(list).toHaveBeenLastCalledWith(0, expect.objectContaining({ paymentStatus: 'COMPLETED' })));
  });

  it('confirms a pending cash-on-delivery payment after asking', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(paymentService, 'getPayments').mockResolvedValue(page([payment(1)]));
    const confirm = vi.spyOn(paymentService, 'confirmCodPayment')
      .mockResolvedValue(payment(1, { paymentStatus: 'COMPLETED', orderConfirmed: true }));

    render(<PaymentsPage />);
    const row = (await screen.findByText('#11')).closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: 'Confirm cash received' }));

    expect(confirm).toHaveBeenCalledWith(1);
    expect(await within(row).findByText('Completed')).toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: 'Confirm cash received' })).not.toBeInTheDocument();
  });

  it('offers no confirmation on PayPal or completed payments', async () => {
    vi.spyOn(paymentService, 'getPayments').mockResolvedValue(page([
      payment(1, { paymentStatus: 'COMPLETED', orderConfirmed: true }),
      payment(2, { paymentMethod: 'PAYPAL' }),
    ]));

    render(<PaymentsPage />);

    await screen.findByText('#11');
    expect(screen.queryByRole('button', { name: 'Confirm cash received' })).not.toBeInTheDocument();
  });

  it('shows why a confirmation was refused', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(paymentService, 'getPayments').mockResolvedValue(page([payment(1)]));
    vi.spyOn(paymentService, 'confirmCodPayment').mockRejectedValue(new ApiError(400, { detail: 'Order 11 is already paid' }));

    render(<PaymentsPage />);
    const row = (await screen.findByText('#11')).closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: 'Confirm cash received' }));

    expect(await screen.findByText('Order 11 is already paid')).toBeInTheDocument();
  });
});
