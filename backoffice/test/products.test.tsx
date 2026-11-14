import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as brandService from '../modules/catalog/services/BrandService';
import * as categoryService from '../modules/catalog/services/CategoryService';
import * as optionService from '../modules/catalog/services/ProductOptionService';
import * as productService from '../modules/catalog/services/ProductService';
import ProductEditPage from '../pages/catalog/products/[id]/edit';
import ProductCreatePage from '../pages/catalog/products/create';
import ProductsPage from '../pages/catalog/products/index';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/catalog/services/BrandService');
vi.mock('../modules/catalog/services/CategoryService');
vi.mock('../modules/catalog/services/ProductOptionService');
vi.mock('../modules/catalog/services/ProductService');

const listItem = (id: number, name: string) => ({
  id,
  name,
  slug: name.toLowerCase().replace(/ /g, '-'),
  isAllowedToOrder: true,
  isPublished: true,
  isFeatured: id === 1,
  isVisibleIndividually: true,
  price: 100 * id,
  createdOn: '2026-11-14T10:00:00Z',
  taxClassId: null,
  parentId: null,
});
const page = (pageNo: number, totalPages: number, names: string[]) => ({
  productContent: names.map((n, i) => listItem(pageNo * 10 + i + 1, n)),
  pageNo,
  pageSize: 10,
  totalElements: totalPages * 10,
  totalPages,
  isLast: pageNo === totalPages - 1,
});

const brands = [
  { id: 1, name: 'Apple', slug: 'apple', isPublish: true },
  { id: 2, name: 'Google', slug: 'google', isPublish: true },
];
const categories = [
  { id: 1, name: 'Phones', slug: 'phones', parentId: -1, categoryImage: null },
  { id: 2, name: 'Accessories', slug: 'accessories', parentId: -1, categoryImage: null },
];
const options = [
  { id: 1, name: 'Color' },
  { id: 2, name: 'Storage' },
];

const emptyLists = {
  productImageIds: [],
  relatedProductIds: [],
  thumbnailMediaId: null,
  taxClassId: null,
  specification: '',
  weight: 0,
  dimensionUnit: 'CM',
  length: 0,
  width: 0,
  height: 0,
  metaTitle: '',
  metaKeyword: '',
  metaDescription: '',
};

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
  vi.mocked(brandService.getBrands).mockResolvedValue(brands);
  vi.mocked(categoryService.getCategories).mockResolvedValue(categories);
  vi.mocked(optionService.getProductOptions).mockResolvedValue(options);
});
afterEach(() => {
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

describe('Products list', () => {
  it('lists the products of the first page', async () => {
    vi.mocked(productService.getProducts).mockResolvedValue(page(0, 2, ['Pixel 9', 'iPhone 16']));

    render(<ProductsPage />);

    const row = (await screen.findByText('Pixel 9')).closest('tr')!;
    expect(within(row).getByText('$100.00')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/catalog/products/1/edit');
    expect(productService.getProducts).toHaveBeenCalledWith(0, '');
    expect(screen.getByText('Page 1 of 2 (20 products)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
  });

  it('searches by name from the first page and pages through the results', async () => {
    vi.mocked(productService.getProducts).mockImplementation(async (pageNo) =>
      page(pageNo, 2, pageNo === 0 ? ['Pixel 9'] : ['Pixel 8'])
    );

    render(<ProductsPage />);
    await screen.findByText('Pixel 9');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Pixel 8')).toBeInTheDocument();
    expect(productService.getProducts).toHaveBeenLastCalledWith(1, '');
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Product name'), 'pixel');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Pixel 9')).toBeInTheDocument();
    expect(productService.getProducts).toHaveBeenLastCalledWith(0, 'pixel');
  });

  it('confirms before deleting a product', async () => {
    vi.mocked(productService.getProducts).mockResolvedValue(page(0, 1, ['Pixel 9']));
    vi.mocked(productService.deleteProduct).mockResolvedValue(null);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<ProductsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(window.confirm).toHaveBeenCalledWith('Delete product "Pixel 9"?');
    expect(productService.deleteProduct).toHaveBeenCalledWith(1);
    expect(await screen.findByText('Product "Pixel 9" deleted.')).toBeInTheDocument();
  });

  it('shows a 400 for bad paging input as a message', async () => {
    vi.mocked(productService.getProducts).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'Bad Request', detail: 'pageSize must be less than or equal to 100', fieldErrors: [] })
    );

    render(<ProductsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('pageSize must be less than or equal to 100');
  });
});

async function fillGeneral() {
  await screen.findByRole('option', { name: 'Google' });
  await userEvent.type(screen.getByLabelText('Name'), 'Pixel 9');
  await userEvent.type(screen.getByLabelText('SKU'), 'PX9');
  await userEvent.type(screen.getByLabelText('GTIN'), '0001');
  await userEvent.clear(screen.getByLabelText('Price'));
  await userEvent.type(screen.getByLabelText('Price'), '799');
  await userEvent.type(screen.getByLabelText('Short description'), 'Google phone');
  await userEvent.type(screen.getByLabelText('Description'), 'The Pixel 9');
  await userEvent.selectOptions(screen.getByLabelText('Brand'), 'Google');
  await userEvent.click(screen.getByLabelText('Phones'));
  await userEvent.click(screen.getByLabelText('Featured'));
}

describe('Create product', () => {
  it('posts a simple product as ProductPostVm', async () => {
    vi.mocked(productService.createProduct).mockResolvedValue({ id: 50 });

    render(<ProductCreatePage />);
    await fillGeneral();
    expect(screen.getByLabelText('Slug')).toHaveValue('pixel-9');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.createProduct).toHaveBeenCalledWith({
      name: 'Pixel 9',
      slug: 'pixel-9',
      sku: 'PX9',
      gtin: '0001',
      price: 799,
      shortDescription: 'Google phone',
      description: 'The Pixel 9',
      brandId: 2,
      categoryIds: [1],
      isPublished: true,
      isFeatured: true,
      isAllowedToOrder: true,
      isVisibleIndividually: true,
      stockTrackingEnabled: false,
      variations: [],
      productOptionValues: [],
      productOptionValueDisplays: [],
      ...emptyLists,
    });
    expect(push).toHaveBeenCalledWith('/catalog/products');
  });

  it('builds variations and productOptionValues from the chosen options', async () => {
    vi.mocked(productService.createProduct).mockResolvedValue({ id: 51 });
    vi.mocked(optionService.createProductOption).mockResolvedValue({ id: 3, name: 'Size' });

    render(<ProductCreatePage />);
    await fillGeneral();
    await userEvent.selectOptions(screen.getByLabelText('Option'), 'Color');
    await userEvent.click(screen.getByRole('button', { name: 'Add option' }));
    await userEvent.type(screen.getByLabelText('New option'), 'Size');
    await userEvent.click(screen.getByRole('button', { name: 'Create option' }));
    expect(optionService.createProductOption).toHaveBeenCalledWith('Size');
    await userEvent.type(await screen.findByLabelText('Color values'), 'Obsidian, Porcelain');
    await userEvent.type(screen.getByLabelText('Size values'), '128GB');
    await userEvent.click(screen.getByRole('button', { name: 'Generate variations' }));

    expect(screen.getByLabelText('Variation 1 name')).toHaveValue('Pixel 9 Obsidian 128GB');
    expect(screen.getByLabelText('Variation 2 name')).toHaveValue('Pixel 9 Porcelain 128GB');
    await userEvent.clear(screen.getByLabelText('Variation 2 price'));
    await userEvent.type(screen.getByLabelText('Variation 2 price'), '849');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.createProduct).toHaveBeenCalledWith(
      expect.objectContaining({
        variations: [
          {
            name: 'Pixel 9 Obsidian 128GB',
            slug: 'pixel-9-obsidian-128gb',
            sku: 'PX9-obsidian-128gb',
            gtin: '',
            price: 799,
            thumbnailMediaId: null,
            productImageIds: [],
            optionValuesByOptionId: { 1: 'Obsidian', 3: '128GB' },
          },
          {
            name: 'Pixel 9 Porcelain 128GB',
            slug: 'pixel-9-porcelain-128gb',
            sku: 'PX9-porcelain-128gb',
            gtin: '',
            price: 849,
            thumbnailMediaId: null,
            productImageIds: [],
            optionValuesByOptionId: { 1: 'Porcelain', 3: '128GB' },
          },
        ],
        productOptionValues: [
          { productOptionId: 1, displayType: 'text', displayOrder: 1, value: ['Obsidian', 'Porcelain'] },
          { productOptionId: 3, displayType: 'text', displayOrder: 2, value: ['128GB'] },
        ],
        productOptionValueDisplays: [
          { productOptionId: 1, displayType: 'text', displayOrder: 1, value: '{"Obsidian":"#000000","Porcelain":"#000000"}' },
          { productOptionId: 3, displayType: 'text', displayOrder: 2, value: '{"128GB":"#000000"}' },
        ],
      })
    );
  });

  it('a removed variation is not sent', async () => {
    vi.mocked(productService.createProduct).mockResolvedValue({ id: 52 });

    render(<ProductCreatePage />);
    await fillGeneral();
    await userEvent.selectOptions(screen.getByLabelText('Option'), 'Color');
    await userEvent.click(screen.getByRole('button', { name: 'Add option' }));
    await userEvent.type(screen.getByLabelText('Color values'), 'Obsidian, Porcelain');
    await userEvent.click(screen.getByRole('button', { name: 'Generate variations' }));
    await userEvent.click(screen.getByRole('button', { name: 'Remove variation 1' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    const payload = vi.mocked(productService.createProduct).mock.calls[0][0];
    expect(payload.variations.map((v) => v.name)).toEqual(['Pixel 9 Porcelain']);
    expect(payload.productOptionValues).toEqual([{ productOptionId: 1, displayType: 'text', displayOrder: 1, value: ['Porcelain'] }]);
  });

  it('shows the 400 detail and field errors', async () => {
    vi.mocked(productService.createProduct).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'Bad Request', detail: 'Slug pixel-9 is already existed or is duplicated', fieldErrors: ['price: must be at least 0'] })
    );

    render(<ProductCreatePage />);
    await fillGeneral();
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Slug pixel-9 is already existed or is duplicated');
    expect(alert).toHaveTextContent('price: must be at least 0');
    expect(push).not.toHaveBeenCalled();
  });
});

describe('Edit product', () => {
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
    stockTrackingEnabled: false,
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
    thumbnailMedia: { id: 31, url: '/x.png' },
    productImageMedias: [{ id: 32, url: '/y.png' }],
    taxClassId: null,
    parentId: null,
  };
  const variation = (id: number, value: string, price: number) => ({
    id,
    name: `Galaxy S24 ${value}`,
    slug: `galaxy-s24-${value.toLowerCase()}`,
    sku: `S24-${value.toLowerCase()}`,
    gtin: null,
    price,
    thumbnail: id === 7 ? { id: 40, url: '/v.png' } : null,
    productImages: [],
    options: { '1': value },
  });

  beforeEach(() => {
    router.query = { id: '10' };
    vi.mocked(productService.getProduct).mockResolvedValue(detail);
    vi.mocked(productService.getProductVariations).mockResolvedValue([variation(7, 'Violet', 799), variation(8, 'Onyx', 819)]);
    vi.mocked(productService.getRelatedProductIds).mockResolvedValue([3, 4]);
    vi.mocked(productService.updateProduct).mockResolvedValue(null);
  });

  it('loads the product, its options and variations', async () => {
    render(<ProductEditPage />);

    expect(await screen.findByLabelText('Name')).toHaveValue('Galaxy S24');
    expect(screen.getByLabelText('Slug')).toHaveValue('galaxy-s24');
    expect(screen.getByLabelText('Price')).toHaveValue(799);
    expect(await screen.findByLabelText('Brand')).toHaveValue('1');
    expect(screen.getByLabelText('Phones')).toBeChecked();
    expect(screen.getByLabelText('Accessories')).not.toBeChecked();
    expect(screen.getByLabelText('Visible individually')).toBeChecked();
    expect(screen.getByLabelText('Color values')).toHaveValue('Violet, Onyx');
    expect(screen.getByLabelText('Variation 2 price')).toHaveValue(819);
    expect(productService.getProduct).toHaveBeenCalledWith(10);
  });

  it('puts ProductPutVm keeping ids, images, related products and fields the form does not show', async () => {
    render(<ProductEditPage />);
    const name = await screen.findByLabelText('Variation 1 price');
    await userEvent.clear(name);
    await userEvent.type(name, '779');
    await userEvent.type(screen.getByLabelText('Color values'), ', Cream');
    await userEvent.click(screen.getByRole('button', { name: 'Generate variations' }));
    expect(screen.getByLabelText('Variation 3 name')).toHaveValue('Galaxy S24 Cream');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.updateProduct).toHaveBeenCalledWith(10, {
      name: 'Galaxy S24',
      slug: 'galaxy-s24',
      sku: 'S24',
      gtin: 'G24',
      price: 799,
      shortDescription: 'Samsung flagship',
      description: 'Long text',
      brandId: 1,
      categoryIds: [1],
      isPublished: true,
      isFeatured: false,
      isAllowedToOrder: true,
      isVisibleIndividually: true,
      stockTrackingEnabled: false,
      specification: 'Spec',
      weight: 0.2,
      dimensionUnit: 'CM',
      length: 15,
      width: 7,
      height: 1,
      metaTitle: 'Galaxy',
      metaKeyword: 'galaxy',
      metaDescription: 'Galaxy S24',
      thumbnailMediaId: 31,
      productImageIds: [32],
      relatedProductIds: [3, 4],
      taxClassId: null,
      variations: [
        { id: 7, name: 'Galaxy S24 Violet', slug: 'galaxy-s24-violet', sku: 'S24-violet', gtin: '', price: 779, thumbnailMediaId: 40, productImageIds: [], optionValuesByOptionId: { 1: 'Violet' } },
        { id: 8, name: 'Galaxy S24 Onyx', slug: 'galaxy-s24-onyx', sku: 'S24-onyx', gtin: '', price: 819, thumbnailMediaId: null, productImageIds: [], optionValuesByOptionId: { 1: 'Onyx' } },
        { id: null, name: 'Galaxy S24 Cream', slug: 'galaxy-s24-cream', sku: 'S24-cream', gtin: '', price: 799, thumbnailMediaId: null, productImageIds: [], optionValuesByOptionId: { 1: 'Cream' } },
      ],
      productOptionValues: [{ productOptionId: 1, displayType: 'text', displayOrder: 1, value: ['Violet', 'Onyx', 'Cream'] }],
      productOptionValueDisplays: [
        { productOptionId: 1, displayType: 'text', displayOrder: 1, value: '{"Violet":"#000000","Onyx":"#000000","Cream":"#000000"}' },
      ],
    });
    expect(push).toHaveBeenCalledWith('/catalog/products');
  });

  it('shows the error when the product does not exist', async () => {
    vi.mocked(productService.getProduct).mockRejectedValue(
      new ApiError(404, { statusCode: '404 NOT_FOUND', title: 'Not Found', detail: 'Product 10 is not found', fieldErrors: [] })
    );

    render(<ProductEditPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Product 10 is not found');
  });
});
