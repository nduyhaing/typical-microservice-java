import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PLACEHOLDER_IMAGE } from '../../../common/utils/image';
import { ProductDetail } from '../models/ProductDetail';
import { ProductOptions } from '../models/ProductOptions';
import { ProductVariation } from '../models/ProductVariation';
import { vi } from 'vitest';
import { ApiError } from '../../../common/services/ApiClientService';
import * as authService from '../../auth/services/AuthenticationService';
import * as cartService from '../../cart/services/CartService';
import ProductDetails from './ProductDetails';

vi.mock('../../cart/services/CartService');
vi.mock('../../auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  redirectToLogin: vi.fn(),
}));

const galaxy: ProductDetail = {
  id: 1,
  name: 'Galaxy S24',
  brandName: 'Samsung',
  productCategories: ['Phones'],
  productAttributeGroups: [
    { name: 'Display', productAttributeValues: [{ name: 'Screen size', value: '6.2 inch' }] },
    { name: 'Battery', productAttributeValues: [{ name: 'Capacity', value: '4000 mAh' }] },
  ],
  shortDescription: 'Samsung flagship phone',
  description: 'A long description.',
  specification: null,
  isAllowedToOrder: true,
  isPublished: true,
  isFeatured: true,
  hasOptions: true,
  price: 799,
  thumbnailMediaUrl: '',
  productImageMediaUrls: [],
};

const options: ProductOptions[] = [{ id: 7, name: 'Color', value: ['Black', 'Violet'] }];
const variations: ProductVariation[] = [
  { id: 2, name: 'Black', slug: 'b', sku: '', gtin: '', price: 799, thumbnail: null, productImages: [], options: { 7: 'Black' } },
  { id: 3, name: 'Violet', slug: 'v', sku: '', gtin: '', price: 819, thumbnail: null, productImages: [], options: { 7: 'Violet' } },
];

const bare: ProductDetail = {
  ...galaxy,
  name: 'USB-C Cable',
  brandName: null,
  productCategories: [],
  productAttributeGroups: [],
  hasOptions: false,
  description: null,
  price: 19,
};

describe('ProductDetails', () => {
  it('renders name, brand, price and grouped specification', () => {
    render(<ProductDetails product={galaxy} options={options} variations={variations} />);

    expect(screen.getByRole('heading', { name: 'Galaxy S24' })).toBeInTheDocument();
    expect(screen.getByText('Samsung')).toBeInTheDocument();
    expect(screen.getByTestId('price')).toHaveTextContent('799');
    expect(screen.getByRole('heading', { name: 'Specification' })).toBeInTheDocument();
    expect(screen.getByText('Display')).toBeInTheDocument();
    expect(screen.getByText('6.2 inch')).toBeInTheDocument();
    expect(screen.getByText('4000 mAh')).toBeInTheDocument();
  });

  it('renders a bare product without empty sections', () => {
    render(<ProductDetails product={bare} options={[]} variations={[]} />);

    expect(screen.getByRole('heading', { name: 'USB-C Cable' })).toBeInTheDocument();
    expect(screen.getByTestId('price')).toHaveTextContent('19');
    expect(screen.queryByRole('heading', { name: 'Specification' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Color' })).not.toBeInTheDocument();
  });

  it('selecting Violet shows the variation price 819', async () => {
    render(<ProductDetails product={galaxy} options={options} variations={variations} />);

    await userEvent.click(screen.getByRole('button', { name: 'Violet' }));

    expect(screen.getByTestId('price')).toHaveTextContent('819');
    expect(screen.getByRole('button', { name: 'Violet' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('shows the placeholder when the product has no image', () => {
    render(<ProductDetails product={bare} options={[]} variations={[]} />);

    expect(screen.getByRole('img', { name: 'USB-C Cable' }).getAttribute('src')).toContain(PLACEHOLDER_IMAGE);
  });

  describe('add to cart', () => {
    afterEach(() => vi.resetAllMocks());

    it('is disabled until a variation is selected', async () => {
      render(<ProductDetails product={galaxy} options={options} variations={variations} />);

      expect(screen.getByRole('button', { name: 'Add to cart' })).toBeDisabled();
      await userEvent.click(screen.getByRole('button', { name: 'Violet' }));
      expect(screen.getByRole('button', { name: 'Add to cart' })).toBeEnabled();
    });

    it('posts the selected variation id and quantity', async () => {
      vi.mocked(cartService.addToCart).mockResolvedValue(undefined);
      render(<ProductDetails product={galaxy} options={options} variations={variations} />);

      await userEvent.click(screen.getByRole('button', { name: 'Violet' }));
      await userEvent.clear(screen.getByLabelText('Quantity'));
      await userEvent.type(screen.getByLabelText('Quantity'), '2');
      await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }));

      expect(cartService.addToCart).toHaveBeenCalledWith(3, 2);
      expect(await screen.findByText('Added to your cart.')).toBeInTheDocument();
    });

    it('adds the product itself when it has no options', async () => {
      vi.mocked(cartService.addToCart).mockResolvedValue(undefined);
      render(<ProductDetails product={bare} options={[]} variations={[]} />);

      await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }));

      expect(cartService.addToCart).toHaveBeenCalledWith(bare.id, 1);
    });

    it('when anonymous, redirects to login', async () => {
      vi.mocked(cartService.addToCart).mockRejectedValue(new ApiError(403, {}));
      render(<ProductDetails product={bare} options={[]} variations={[]} />);

      await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }));

      expect(authService.redirectToLogin).toHaveBeenCalled();
    });
  });
});
