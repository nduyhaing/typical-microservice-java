import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as brandService from '../modules/catalog/services/BrandService';
import * as categoryService from '../modules/catalog/services/CategoryService';
import * as optionService from '../modules/catalog/services/ProductOptionService';
import * as productService from '../modules/catalog/services/ProductService';
import * as taxClassService from '../modules/tax/services/TaxClassService';
import ProductEditPage from '../pages/catalog/products/[id]/edit';
import ProductCreatePage from '../pages/catalog/products/create';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/catalog/services/BrandService');
vi.mock('../modules/catalog/services/CategoryService');
vi.mock('../modules/catalog/services/ProductOptionService');
vi.mock('../modules/catalog/services/ProductService');
vi.mock('../modules/tax/services/TaxClassService');

const detail = {
  id: 10,
  name: 'Galaxy S24',
  shortDescription: 'Samsung flagship',
  description: 'Long text',
  specification: 'Spec',
  sku: 'S24',
  gtin: 'G24',
  slug: 'galaxy-s24',
  isAllowedToOrder: true,
  isPublished: true,
  isFeatured: false,
  isVisible: true,
  stockTrackingEnabled: true,
  weight: 0.2,
  dimensionUnit: 'CM',
  length: 15,
  width: 7,
  height: 1,
  price: 799,
  brandId: 1,
  categories: [{ id: 1, name: 'Phones' }],
  metaTitle: 'Galaxy',
  metaKeyword: 'galaxy',
  metaDescription: 'Galaxy S24',
  thumbnailMedia: null,
  productImageMedias: [],
  taxClassId: 2,
  parentId: null,
};

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
  vi.mocked(brandService.getBrands).mockResolvedValue([{ id: 1, name: 'Samsung', slug: 'samsung', isPublish: true }]);
  vi.mocked(categoryService.getCategories).mockResolvedValue([
    { id: 1, name: 'Phones', slug: 'phones', parentId: -1, categoryImage: null },
  ]);
  vi.mocked(optionService.getProductOptions).mockResolvedValue([]);
  vi.mocked(taxClassService.getTaxClasses).mockResolvedValue([
    { id: 1, name: 'Standard' },
    { id: 2, name: 'Reduced' },
  ]);
});
afterEach(() => {
  vi.resetAllMocks();
});

describe('Product tax class', () => {
  it('a new product sends the chosen tax class id', async () => {
    vi.mocked(productService.createProduct).mockResolvedValue({ id: 50 });

    render(<ProductCreatePage />);
    await screen.findByRole('option', { name: 'Reduced' });
    expect(screen.getByLabelText('Tax class')).toHaveValue('');
    await userEvent.type(screen.getByLabelText('Name'), 'Pixel 9');
    await userEvent.type(screen.getByLabelText('Price'), '799');
    await userEvent.selectOptions(screen.getByLabelText('Tax class'), 'Reduced');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.createProduct).toHaveBeenCalledWith(expect.objectContaining({ name: 'Pixel 9', taxClassId: 2 }));
  });

  it('a new product without a tax class sends null', async () => {
    vi.mocked(productService.createProduct).mockResolvedValue({ id: 51 });

    render(<ProductCreatePage />);
    await screen.findByRole('option', { name: 'Reduced' });
    await userEvent.type(screen.getByLabelText('Name'), 'Pixel 9');
    await userEvent.type(screen.getByLabelText('Price'), '799');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.createProduct).toHaveBeenCalledWith(expect.objectContaining({ taxClassId: null }));
  });

  describe('edit', () => {
    beforeEach(() => {
      router.query = { id: '10' };
      vi.mocked(productService.getProduct).mockResolvedValue(detail);
      vi.mocked(productService.getProductVariations).mockResolvedValue([]);
      vi.mocked(productService.getRelatedProductIds).mockResolvedValue([3]);
      vi.mocked(productService.updateProduct).mockResolvedValue(null);
    });

    it('shows and keeps the product tax class with the other fields', async () => {
      render(<ProductEditPage />);
      await screen.findByRole('option', { name: 'Reduced' });
      expect(screen.getByLabelText('Tax class')).toHaveValue('2');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(productService.updateProduct).toHaveBeenCalledWith(
        10,
        expect.objectContaining({
          taxClassId: 2,
          name: 'Galaxy S24',
          stockTrackingEnabled: true,
          specification: 'Spec',
          weight: 0.2,
          metaTitle: 'Galaxy',
          relatedProductIds: [3],
          brandId: 1,
        })
      );
    });

    it('choosing None clears the tax class', async () => {
      render(<ProductEditPage />);
      await screen.findByRole('option', { name: 'Reduced' });
      await userEvent.selectOptions(screen.getByLabelText('Tax class'), 'None');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(productService.updateProduct).toHaveBeenCalledWith(10, expect.objectContaining({ taxClassId: null }));
    });

    it('keeps a tax class that no longer exists in the list', async () => {
      vi.mocked(productService.getProduct).mockResolvedValue({ ...detail, taxClassId: 99 });

      render(<ProductEditPage />);
      await screen.findByRole('option', { name: 'Reduced' });
      expect(screen.getByLabelText('Tax class')).toHaveValue('99');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(productService.updateProduct).toHaveBeenCalledWith(10, expect.objectContaining({ taxClassId: 99 }));
    });
  });
});
