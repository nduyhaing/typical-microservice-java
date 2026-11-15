import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import * as api from '../common/services/ApiClientService';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as orderService from '../modules/order/services/OrderService';
import * as paymentService from '../modules/payment/services/PaymentService';
import PayPage from '../pages/checkout/pay/[orderId]';
import PaymentReturnPage from '../pages/checkout/payment-return';
import MyOrdersPage from '../pages/my-orders';

const router = { push: vi.fn(), query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
}));

const order = (id: number, patch = {}) => ({
  id,
  email: 'customer@shopapp.local',
  checkoutId: `c-${id}`,
  note: null,
  numberItem: 1,
  totalPrice: 819,
  orderStatus: 'PENDING_PAYMENT',
  paymentStatus: 'PENDING',
  paymentMethod: 'COD',
  createdOn: '2026-11-14T12:00:00Z',
  orderItemVms: [{ productId: 25, productName: 'Galaxy S24 Violet', quantity: 1, productPrice: 819 }],
  cancellable: true,
  ...patch,
});

const COD = { id: 'COD', name: 'Cash on Delivery', configureUrl: 'cod-config', version: 0 };
const PAYPAL = { id: 'PAYPAL', name: 'PayPal', configureUrl: 'paypal-config', version: 0 };

const assign = vi.fn();

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: null });
  Object.defineProperty(window, 'location', { configurable: true, value: { ...window.location, assign } });
});
afterEach(() => {
  vi.restoreAllMocks();
  assign.mockReset();
});

describe('PaymentService', () => {
  it('init sends only the order id and the method — never an amount', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({ status: 'PENDING', paymentId: 3, redirectUrl: null });
    await paymentService.initPayment(7, 'COD');
    expect(post).toHaveBeenCalledWith('/payment/init', { orderId: 7, paymentMethod: 'COD' });
  });

  it('capture sends the PayPal token, cancel passes it as a query parameter, providers are the storefront list',
    async () => {
      const post = vi.spyOn(api, 'post').mockResolvedValue({});
      const get = vi.spyOn(api, 'get').mockResolvedValue([]);
      await paymentService.capturePayment('5O190127TN364715T');
      await paymentService.cancelPayment('5O190127TN364715T');
      await paymentService.getPaymentProviders();
      expect(post).toHaveBeenCalledWith('/payment/capture', { token: '5O190127TN364715T' });
      expect(get).toHaveBeenCalledWith('/payment/cancel?token=5O190127TN364715T');
      expect(get).toHaveBeenCalledWith('/payment/storefront/payment-providers');
    });
});

describe('Pay page', () => {
  beforeEach(() => {
    router.query = { orderId: '7' };
    vi.spyOn(orderService, 'getMyOrders').mockResolvedValue([order(7)]);
  });

  it('offers the available providers and the order total', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([COD, PAYPAL]);

    render(<PayPage />);

    expect(await screen.findByRole('radio', { name: 'Cash on Delivery' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'PayPal' })).toBeInTheDocument();
    expect(screen.getByTestId('pay-total')).toHaveTextContent('$819.00');
  });

  it('cash on delivery: "Order placed — pay on delivery"', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([COD, PAYPAL]);
    const init = vi.spyOn(paymentService, 'initPayment')
      .mockResolvedValue({ status: 'PENDING', paymentId: 3, redirectUrl: null });

    render(<PayPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Pay' }));

    expect(init).toHaveBeenCalledWith(7, 'COD');
    expect(await screen.findByText(/Order placed — pay on delivery/)).toBeInTheDocument();
    expect(assign).not.toHaveBeenCalled();
  });

  it('PayPal: redirects the browser to the approval URL', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([COD, PAYPAL]);
    vi.spyOn(paymentService, 'initPayment').mockResolvedValue({
      status: 'PAYER_ACTION_REQUIRED',
      paymentId: 4,
      redirectUrl: 'https://www.sandbox.paypal.com/checkoutnow?token=PP1',
    });

    render(<PayPage />);
    await userEvent.click(await screen.findByRole('radio', { name: 'PayPal' }));
    await userEvent.click(screen.getByRole('button', { name: 'Pay' }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://www.sandbox.paypal.com/checkoutnow?token=PP1'));
  });

  it('without PayPal configured, only cash on delivery is offered', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([COD]);

    render(<PayPage />);

    expect(await screen.findByRole('radio', { name: 'Cash on Delivery' })).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: 'PayPal' })).not.toBeInTheDocument();
  });

  it('shows the server reason when the payment is refused', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([COD]);
    vi.spyOn(paymentService, 'initPayment').mockRejectedValue(
      new ApiError(400, { detail: 'Order 7 is already paid' })
    );

    render(<PayPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Pay' }));

    expect(await screen.findByText('Order 7 is already paid')).toBeInTheDocument();
  });

  it('an order that is already paid has nothing to pay', async () => {
    vi.spyOn(orderService, 'getMyOrders').mockResolvedValue([
      order(7, { orderStatus: 'PAID', paymentStatus: 'COMPLETED', cancellable: false }),
    ]);
    vi.spyOn(paymentService, 'getPaymentProviders').mockResolvedValue([COD]);

    render(<PayPage />);

    expect(await screen.findByText(/already paid/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pay' })).not.toBeInTheDocument();
  });

  it('payments down: says so and links to my orders', async () => {
    vi.spyOn(paymentService, 'getPaymentProviders').mockRejectedValue(new ApiError(502, null));

    render(<PayPage />);

    const notice = await screen.findByText(/Payments are not available right now/);
    expect(within(notice).getByRole('link', { name: 'My orders' })).toHaveAttribute('href', '/my-orders');
  });
});

describe('PayPal return page', () => {
  it('captures server-side with the token and shows success', async () => {
    router.query = { token: 'PP1', PayerID: 'X' };
    const capture = vi.spyOn(paymentService, 'capturePayment').mockResolvedValue({
      orderId: 7,
      paymentId: 4,
      paymentStatus: 'COMPLETED',
    } as paymentService.CaptureResult);

    render(<PaymentReturnPage />);

    expect(await screen.findByText(/Payment successful/)).toBeInTheDocument();
    expect(capture).toHaveBeenCalledTimes(1);
    expect(capture).toHaveBeenCalledWith('PP1');
    expect(screen.getByText(/order #7 is paid/i)).toBeInTheDocument();
  });

  it('a refused capture shows the reason and does not claim success', async () => {
    router.query = { token: 'PP1' };
    vi.spyOn(paymentService, 'capturePayment').mockRejectedValue(
      new ApiError(400, { title: 'ORDER_NOT_APPROVED', detail: 'PayPal refused the request: ORDER_NOT_APPROVED' })
    );

    render(<PaymentReturnPage />);

    expect(await screen.findByText(/Payment failed/)).toBeInTheDocument();
    expect(screen.getByText('PayPal refused the request: ORDER_NOT_APPROVED')).toBeInTheDocument();
    expect(screen.queryByText(/Payment successful/)).not.toBeInTheDocument();
  });

  it('a 503 offers to try again (a replayed capture never pays twice)', async () => {
    router.query = { token: 'PP1' };
    const capture = vi.spyOn(paymentService, 'capturePayment')
      .mockRejectedValueOnce(new ApiError(503, { detail: 'later' }))
      .mockResolvedValueOnce({ orderId: 7, paymentId: 4, paymentStatus: 'COMPLETED' } as paymentService.CaptureResult);

    render(<PaymentReturnPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Try again' }));

    expect(await screen.findByText(/Payment successful/)).toBeInTheDocument();
    expect(capture).toHaveBeenCalledTimes(2);
  });

  it('cancelled at PayPal: cancels the payment and offers to pay again', async () => {
    router.query = { token: 'PP1', cancelled: 'true' };
    const capture = vi.spyOn(paymentService, 'capturePayment');
    const cancel = vi.spyOn(paymentService, 'cancelPayment')
      .mockResolvedValue({ paymentId: 4, orderId: 7, paymentStatus: 'CANCELLED' });

    render(<PaymentReturnPage />);

    expect(await screen.findByText(/Payment cancelled/)).toBeInTheDocument();
    expect(cancel).toHaveBeenCalledWith('PP1');
    expect(capture).not.toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'Choose another way to pay' })).toHaveAttribute('href', '/checkout/pay/7');
  });

  it('without a token there is nothing to capture', async () => {
    router.query = {};
    const capture = vi.spyOn(paymentService, 'capturePayment');

    render(<PaymentReturnPage />);

    expect(await screen.findByText(/No payment to complete/)).toBeInTheDocument();
    expect(capture).not.toHaveBeenCalled();
  });
});

describe('My orders: payment', () => {
  function card(id: number) {
    return screen.getByText(`Order #${id}`).closest('.card') as HTMLElement;
  }

  it('shows the payment status and a "Pay now" link on unpaid orders only', async () => {
    vi.spyOn(orderService, 'getMyOrders').mockResolvedValue([
      order(7),
      order(8, { orderStatus: 'PAID', paymentStatus: 'COMPLETED', cancellable: false }),
      order(9, { orderStatus: 'CANCELLED', paymentStatus: 'CANCELLED', cancellable: false }),
    ]);

    render(<MyOrdersPage />);

    await screen.findByText('Order #7');
    expect(within(card(7)).getByText('Payment: Pending')).toBeInTheDocument();
    expect(within(card(7)).getByRole('link', { name: 'Pay now' })).toHaveAttribute('href', '/checkout/pay/7');
    expect(within(card(8)).getByText('Payment: Completed')).toBeInTheDocument();
    expect(within(card(8)).queryByRole('link', { name: 'Pay now' })).not.toBeInTheDocument();
    expect(within(card(9)).queryByRole('link', { name: 'Pay now' })).not.toBeInTheDocument();
  });
});
