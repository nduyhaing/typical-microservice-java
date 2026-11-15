import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as addressService from '../modules/address/services/AddressService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as cartService from '../modules/cart/services/CartService';
import * as orderService from '../modules/order/services/OrderService';
import CartPage from '../pages/cart';
import CheckoutPage from '../pages/checkout/[id]';
import MyOrdersPage from '../pages/my-orders';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
}));
vi.mock('../modules/cart/services/CartService');
vi.mock('../modules/order/services/OrderService');
vi.mock('../modules/address/services/AddressService');

const violet = { productId: 25, name: 'Galaxy S24 Violet', slug: 'galaxy-s24-violet', thumbnailUrl: '', price: 819, quantity: 2, available: true };
const checkout = {
  id: 'c-1',
  email: 'customer@shopapp.local',
  note: null,
  checkoutState: 'PENDING',
  totalAmount: 1638,
  checkoutItemVms: [{ id: 1, productId: 25, productName: 'Galaxy S24 Violet', quantity: 2, productPrice: 819 }],
};
const order = {
  id: 7,
  email: 'customer@shopapp.local',
  checkoutId: 'c-1',
  note: null,
  numberItem: 2,
  totalPrice: 1638,
  orderStatus: 'PENDING_PAYMENT',
  paymentStatus: 'PENDING',
  paymentMethod: 'COD',
  createdOn: '2026-11-14T12:00:00Z',
  orderItemVms: [{ productId: 25, productName: 'Galaxy S24 Violet', quantity: 2, productPrice: 819 }],
};

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: false, authenticatedUser: null });
});
afterEach(() => vi.resetAllMocks());

describe('Cart page checkout button', () => {
  it('creates a checkout from the cart and navigates to it', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue([violet]);
    vi.mocked(orderService.createCheckout).mockResolvedValue(checkout);

    render(<CartPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Checkout' }));

    expect(orderService.createCheckout).toHaveBeenCalledWith([{ productId: 25, quantity: 2 }]);
    expect(push).toHaveBeenCalledWith('/checkout/c-1');
  });

  it('is disabled while the cart has unavailable lines', async () => {
    vi.mocked(cartService.getCartLines).mockResolvedValue([
      violet,
      { ...violet, productId: 9, name: 'Product no longer available', available: false },
    ]);

    render(<CartPage />);

    expect(await screen.findByRole('button', { name: 'Checkout' })).toBeDisabled();
  });
});

describe('Checkout page', () => {
  const home = {
    id: 101,
    contactName: 'Customer Home',
    phone: '0901',
    addressLine1: '1 Le Loi',
    addressLine2: null,
    city: 'Ho Chi Minh City',
    zipCode: '700000',
    districtId: 100,
    districtName: 'District 1',
    stateOrProvinceId: 10,
    stateOrProvinceName: 'Ho Chi Minh',
    countryId: 1,
    countryName: 'Viet Nam',
    isActive: true,
  };
  const office = { ...home, id: 102, contactName: 'Customer Office', countryId: 2, countryName: 'United States', isActive: false };
  const taxedPreview = {
    checkoutId: 'c-1',
    subtotal: 1638,
    totalTax: 163.8,
    discount: 0,
    deliveryFee: 0,
    total: 1801.8,
    items: [
      { productId: 25, productName: 'Galaxy S24 Violet', quantity: 2, productPrice: 819, subtotal: 1638, taxPercent: 10, taxAmount: 163.8 },
    ],
  };
  const untaxedPreview = { ...taxedPreview, totalTax: 0, total: 1638 };

  beforeEach(() => {
    router.query = { id: 'c-1' };
    vi.mocked(orderService.getCheckout).mockResolvedValue(checkout);
    vi.mocked(addressService.getUserAddresses).mockResolvedValue([office, home]);
    vi.mocked(addressService.getCountries).mockResolvedValue([
      { id: 1, code2: 'VN', name: 'Viet Nam' },
      { id: 2, code2: 'US', name: 'United States' },
    ]);
    vi.mocked(addressService.getStatesOrProvinces).mockResolvedValue([]);
    vi.mocked(addressService.getDistricts).mockResolvedValue([]);
    vi.mocked(orderService.previewOrder).mockImplementation(async (_id, address) =>
      'shippingAddressId' in address && address.shippingAddressId === 101 ? taxedPreview : untaxedPreview
    );
  });

  it('preselects the default saved address and shows the server subtotal, tax and total', async () => {
    render(<CheckoutPage />);

    expect(await screen.findByText('Galaxy S24 Violet')).toBeInTheDocument();
    expect(await screen.findByRole('radio', { name: /Customer Home/ })).toBeChecked();
    await waitFor(() => expect(screen.getByTestId('checkout-tax')).toHaveTextContent('$163.80'));
    expect(screen.getByTestId('checkout-subtotal')).toHaveTextContent('$1,638.00');
    expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,801.80');
    expect(orderService.previewOrder).toHaveBeenCalledWith('c-1', { shippingAddressId: 101 });
    expect(screen.getByText('Cash on delivery')).toBeInTheDocument();
  });

  it("shows each line's amount from the server preview, never multiplied in the browser", async () => {
    // a server line subtotal that browser math (819 x 2 = 1638) would not give
    vi.mocked(orderService.previewOrder).mockResolvedValue({
      ...taxedPreview,
      items: [{ ...taxedPreview.items[0], subtotal: 1637.99 }],
    });
    render(<CheckoutPage />);

    await waitFor(() => expect(screen.getByTestId('checkout-line-1')).toHaveTextContent('$1,637.99'));
  });

  it('refreshes the summary from the server when another address is chosen', async () => {
    render(<CheckoutPage />);
    await waitFor(() => expect(screen.getByTestId('checkout-tax')).toHaveTextContent('$163.80'));

    await userEvent.click(screen.getByRole('radio', { name: /Customer Office/ }));

    expect(orderService.previewOrder).toHaveBeenLastCalledWith('c-1', { shippingAddressId: 102 });
    await waitFor(() => expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,638.00'));
    expect(screen.getByTestId('checkout-tax')).toHaveTextContent('$0.00');
  });

  it('places the order with the chosen saved address and goes to my orders', async () => {
    vi.mocked(orderService.placeOrder).mockResolvedValue(order);

    render(<CheckoutPage />);
    await placeWithDefaultAddress();

    expect(orderService.placeOrder).toHaveBeenCalledWith({
      checkoutId: 'c-1',
      shippingAddressId: 101,
      note: undefined,
      paymentMethod: 'COD',
    });
    expect(push).toHaveBeenCalledWith('/my-orders?placed=7');
  });

  it('without saved addresses, a new address from the form is priced and sent', async () => {
    vi.mocked(addressService.getUserAddresses).mockResolvedValue([]);
    vi.mocked(orderService.placeOrder).mockResolvedValue(order);

    render(<CheckoutPage />);
    const place = await screen.findByRole('button', { name: 'Place order' });
    expect(place).toBeDisabled();
    await userEvent.type(await screen.findByLabelText('Contact name *'), 'Customer Dev');
    await userEvent.type(screen.getByLabelText('Phone *'), '0123456789');
    await userEvent.type(screen.getByLabelText('Address *'), '1 Main St');
    await screen.findByRole('option', { name: 'United States' });
    await userEvent.selectOptions(screen.getByLabelText('Country *'), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Use this address' }));

    const typed = expect.objectContaining({ contactName: 'Customer Dev', addressLine1: '1 Main St', countryId: 2 });
    expect(await screen.findByTestId('new-address')).toHaveTextContent('Customer Dev, 1 Main St');
    expect(orderService.previewOrder).toHaveBeenCalledWith('c-1', { shippingAddressPostVm: typed });
    await waitFor(() => expect(place).toBeEnabled());
    await userEvent.click(place);
    expect(orderService.placeOrder).toHaveBeenCalledWith(expect.objectContaining({ shippingAddressPostVm: typed }));
  });

  it('cannot place the order when tax cannot be calculated', async () => {
    vi.mocked(orderService.previewOrder).mockRejectedValue(new ApiError(500, {}));

    render(<CheckoutPage />);

    expect(await screen.findByText('Tax could not be calculated right now. Please try again later.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Place order' })).toBeDisabled();
    expect(screen.getByTestId('checkout-total')).toHaveTextContent('—');
  });

  async function placeWithDefaultAddress() {
    await waitFor(() => expect(screen.getByRole('button', { name: 'Place order' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
  }

  it('an expired session while placing the order shows the login link', async () => {
    vi.mocked(orderService.placeOrder).mockRejectedValue(new ApiError(401, {}));

    render(<CheckoutPage />);
    await placeWithDefaultAddress();

    expect(await screen.findByRole('link', { name: 'Log in again' })).toBeInTheDocument();
  });

  it('an already ordered checkout points to my orders', async () => {
    vi.mocked(orderService.placeOrder).mockRejectedValue(
      new ApiError(400, { detail: 'Checkout c-1 has already been ordered' })
    );

    render(<CheckoutPage />);
    await placeWithDefaultAddress();

    expect(await screen.findByRole('link', { name: 'See my orders' })).toHaveAttribute('href', '/my-orders');
  });

  it('not enough stock shows the server message and a way back to the cart', async () => {
    vi.mocked(orderService.placeOrder).mockRejectedValue(
      new ApiError(400, { detail: 'Not enough stock for Galaxy S24 Violet (product 25)' })
    );

    render(<CheckoutPage />);
    await placeWithDefaultAddress();

    expect(await screen.findByText(/Not enough stock for Galaxy S24 Violet/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the cart' })).toHaveAttribute('href', '/cart');
  });

  it('limits the note to 255 characters', async () => {
    render(<CheckoutPage />);

    expect(await screen.findByLabelText('Note (optional)')).toHaveAttribute('maxLength', '255');
  });

  it('shows a login prompt on 401/403', async () => {
    vi.mocked(orderService.getCheckout).mockRejectedValue(new ApiError(401, {}));

    render(<CheckoutPage />);

    expect(await screen.findByRole('link', { name: 'Log in to check out' })).toBeInTheDocument();
  });

  describe('coupon', () => {
    // 10 % off 1638.00 = 163.80; tax on the discounted amount (1474.20 x 10 % = 147.42)
    const discountedPreview = {
      ...taxedPreview,
      discount: 163.8,
      totalTax: 147.42,
      total: 1621.62,
      couponCode: 'SAVE10',
      items: [{ ...taxedPreview.items[0], discountAmount: 163.8, taxAmount: 147.42 }],
    };
    const promotionError = (title: string, detail: string) => new ApiError(400, { title, detail });

    beforeEach(() => {
      vi.mocked(orderService.previewOrder).mockImplementation(async (_id, _address, couponCode) => {
        if (couponCode === undefined) {
          return taxedPreview;
        }
        if (couponCode === 'SAVE10' || couponCode === 'save10') {
          return discountedPreview;
        }
        throw promotionError('PROMOTION_NOT_FOUND', `Promotion ${couponCode} is not found`);
      });
    });

    async function applyCoupon(code: string) {
      await waitFor(() => expect(screen.getByTestId('checkout-tax')).toHaveTextContent('$163.80'));
      await userEvent.type(screen.getByLabelText('Coupon code'), code);
      await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    }

    it('applying a code re-prices the order on the server and shows the discount line', async () => {
      render(<CheckoutPage />);
      expect(await screen.findByRole('button', { name: 'Apply' })).toBeDisabled();
      expect(screen.queryByTestId('checkout-discount')).not.toBeInTheDocument();

      await applyCoupon(' save10 ');

      expect(orderService.previewOrder).toHaveBeenCalledWith('c-1', { shippingAddressId: 101 }, 'save10');
      await waitFor(() => expect(screen.getByTestId('checkout-discount')).toHaveTextContent('−$163.80'));
      expect(screen.getByTestId('checkout-tax')).toHaveTextContent('$147.42');
      expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,621.62');
      expect(screen.getByText(/Coupon SAVE10 applied/)).toBeInTheDocument();
    });

    it('removing the coupon prices the order without it again', async () => {
      render(<CheckoutPage />);
      await applyCoupon('SAVE10');
      await waitFor(() => expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,621.62'));

      await userEvent.click(screen.getByRole('button', { name: 'Remove' }));

      await waitFor(() => expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,801.80'));
      expect(screen.queryByTestId('checkout-discount')).not.toBeInTheDocument();
      expect(orderService.previewOrder).toHaveBeenLastCalledWith('c-1', { shippingAddressId: 101 });
      expect(screen.getByLabelText('Coupon code')).toHaveValue('');
    });

    it.each([
      ['PROMOTION_NOT_FOUND', 'This coupon code does not exist.'],
      ['PROMOTION_NOT_STARTED', 'This coupon is not valid yet.'],
      ['PROMOTION_EXPIRED', 'This coupon has expired.'],
      ['PROMOTION_USED_UP', 'This coupon has been used up.'],
      ['MINIMUM_ORDER_NOT_REACHED', 'Your order does not reach the minimum amount for this coupon.'],
      ['PROMOTION_NOT_APPLICABLE', 'This coupon does not apply to the products in your order.'],
    ])('a refused code (%s) shows a friendly message and keeps the order priced without it', async (code, text) => {
      vi.mocked(orderService.previewOrder).mockImplementation(async (_id, _address, couponCode) => {
        if (couponCode === undefined) {
          return taxedPreview;
        }
        throw promotionError(code, 'server text');
      });
      vi.mocked(orderService.placeOrder).mockResolvedValue(order);
      render(<CheckoutPage />);

      await applyCoupon('BAD');

      expect(await screen.findByText(text)).toBeInTheDocument();
      expect(screen.queryByTestId('checkout-discount')).not.toBeInTheDocument();
      expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,801.80');
      await placeWithDefaultAddress();
      expect(orderService.placeOrder).toHaveBeenCalledWith(expect.not.objectContaining({ couponCode: expect.anything() }));
    });

    it('a coupon that cannot be checked right now says so', async () => {
      vi.mocked(orderService.previewOrder).mockImplementation(async (_id, _address, couponCode) => {
        if (couponCode === undefined) {
          return taxedPreview;
        }
        throw new ApiError(500, { detail: 'Promotion service is unavailable, please try again later' });
      });
      render(<CheckoutPage />);

      await applyCoupon('SAVE10');

      expect(await screen.findByText('The coupon could not be checked right now. Please try again later.')).toBeInTheDocument();
      expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,801.80');
    });

    it('places the order with the applied code', async () => {
      vi.mocked(orderService.placeOrder).mockResolvedValue(order);
      render(<CheckoutPage />);
      await applyCoupon('SAVE10');
      await waitFor(() => expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,621.62'));

      await placeWithDefaultAddress();

      expect(orderService.placeOrder).toHaveBeenCalledWith({
        checkoutId: 'c-1',
        shippingAddressId: 101,
        note: undefined,
        paymentMethod: 'COD',
        couponCode: 'SAVE10',
      });
    });

    it('a coupon used up while placing the order says so and offers to remove it', async () => {
      vi.mocked(orderService.placeOrder).mockRejectedValue(
        promotionError('PROMOTION_USED_UP', 'Promotion SAVE10 has been used up')
      );
      render(<CheckoutPage />);
      await applyCoupon('SAVE10');
      await waitFor(() => expect(screen.getByTestId('checkout-total')).toHaveTextContent('$1,621.62'));

      await placeWithDefaultAddress();

      expect(await screen.findByText(/This coupon has been used up\. Remove it to order without the discount\./)).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Back to the cart' })).not.toBeInTheDocument();
    });
  });
});

describe('My orders page', () => {
  it('lists the orders with status and total', async () => {
    vi.mocked(orderService.getMyOrders).mockResolvedValue([order]);

    render(<MyOrdersPage />);

    expect(await screen.findByText('Order #7')).toBeInTheDocument();
    expect(screen.getByText('Pending payment')).toBeInTheDocument();
    expect(screen.getByTestId('order-total-7')).toHaveTextContent('$1,638.00');
    expect(screen.getByText(/Galaxy S24 Violet/)).toBeInTheDocument();
  });

  it('shows the tax included in the total', async () => {
    vi.mocked(orderService.getMyOrders).mockResolvedValue([{ ...order, tax: 163.8, totalPrice: 1801.8 }]);

    render(<MyOrdersPage />);

    expect(await screen.findByTestId('order-tax-7')).toHaveTextContent('incl. tax $163.80');
    expect(screen.getByTestId('order-total-7')).toHaveTextContent('$1,801.80');
  });

  it('confirms a just-placed order', async () => {
    router.query = { placed: '7' };
    vi.mocked(orderService.getMyOrders).mockResolvedValue([order]);

    render(<MyOrdersPage />);

    expect(await screen.findByText('Thank you! Your order #7 has been placed.')).toBeInTheDocument();
  });

  it('shows the empty state', async () => {
    vi.mocked(orderService.getMyOrders).mockResolvedValue([]);

    render(<MyOrdersPage />);

    expect(await screen.findByText('You have no orders yet.')).toBeInTheDocument();
  });

  it('shows a login prompt when anonymous', async () => {
    vi.mocked(orderService.getMyOrders).mockRejectedValue(new ApiError(403, {}));

    render(<MyOrdersPage />);

    expect(await screen.findByRole('link', { name: 'Log in to see your orders' })).toBeInTheDocument();
  });
});
