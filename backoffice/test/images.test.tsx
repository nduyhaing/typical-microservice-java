import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as brandService from '../modules/catalog/services/BrandService';
import * as categoryService from '../modules/catalog/services/CategoryService';
import * as optionService from '../modules/catalog/services/ProductOptionService';
import * as productService from '../modules/catalog/services/ProductService';
import * as mediaService from '../modules/media/services/MediaService';
import CategoryEditPage from '../pages/catalog/categories/[id]/edit';
import CategoryCreatePage from '../pages/catalog/categories/create';
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
vi.mock('../modules/media/services/MediaService');

const png = (name: string) => new File([new Uint8Array([1])], name, { type: 'image/png' });

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
  vi.mocked(brandService.getBrands).mockResolvedValue([{ id: 1, name: 'Google', slug: 'google', isPublish: true }]);
  vi.mocked(categoryService.getCategories).mockResolvedValue([
    { id: 1, name: 'Phones', slug: 'phones', parentId: -1, categoryImage: null },
  ]);
  vi.mocked(optionService.getProductOptions).mockResolvedValue([]);
});
afterEach(() => {
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

async function fillProduct() {
  await screen.findByRole('option', { name: 'Google' });
  await userEvent.type(screen.getByLabelText('Name'), 'Pixel 9');
  await userEvent.clear(screen.getByLabelText('Price'));
  await userEvent.type(screen.getByLabelText('Price'), '799');
}

describe('Product images', () => {
  it('uploads a thumbnail and gallery images, shows previews and sends their ids', async () => {
    vi.mocked(productService.createProduct).mockResolvedValue({ id: 52 });
    let next = 70;
    vi.mocked(mediaService.uploadImage).mockImplementation(async (file: File) => ({
      id: ++next,
      url: `http://m/${file.name}`,
    }));

    render(<ProductCreatePage />);
    await fillProduct();
    await userEvent.upload(screen.getByLabelText('Thumbnail'), png('front.png'));
    expect(await screen.findByRole('img', { name: 'Image 71' })).toHaveAttribute('src', 'http://m/front.png');
    await userEvent.upload(screen.getByLabelText('Product images'), [png('a.png'), png('b.png')]);
    expect(await screen.findByRole('img', { name: 'Image 73' })).toHaveAttribute('src', 'http://m/b.png');
    expect(mediaService.uploadImage).toHaveBeenCalledTimes(3);
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.createProduct).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Pixel 9', thumbnailMediaId: 71, productImageIds: [72, 73] })
    );
  });

  it('a new thumbnail replaces the old one; remove drops the id', async () => {
    vi.mocked(productService.createProduct).mockResolvedValue({ id: 53 });
    let next = 80;
    vi.mocked(mediaService.uploadImage).mockImplementation(async () => {
      next += 1;
      return { id: next, url: `http://m/${next}.png` };
    });

    render(<ProductCreatePage />);
    await fillProduct();
    await userEvent.upload(screen.getByLabelText('Thumbnail'), png('one.png'));
    await screen.findByRole('img', { name: 'Image 81' });
    await userEvent.upload(screen.getByLabelText('Thumbnail'), png('two.png'));
    await screen.findByRole('img', { name: 'Image 82' });
    expect(screen.queryByRole('img', { name: 'Image 81' })).not.toBeInTheDocument();
    await userEvent.upload(screen.getByLabelText('Product images'), [png('a.png'), png('b.png')]);
    await screen.findByRole('img', { name: 'Image 84' });
    await userEvent.click(screen.getByRole('button', { name: 'Remove image 83' }));
    await userEvent.click(screen.getByRole('button', { name: 'Remove image 82' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.createProduct).toHaveBeenCalledWith(
      expect.objectContaining({ thumbnailMediaId: null, productImageIds: [84] })
    );
  });

  it('a refused upload shows the error and keeps the form', async () => {
    vi.mocked(mediaService.uploadImage).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'Bad Request', detail: 'File type not allowed', fieldErrors: [] })
    );

    render(<ProductCreatePage />);
    await fillProduct();
    await userEvent.upload(screen.getByLabelText('Thumbnail'), png('fake.png'));

    expect(await screen.findByRole('alert')).toHaveTextContent('File type not allowed');
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toHaveValue('Pixel 9');
  });

  it('edit shows the existing images and keeps their ids, plus a new gallery image', async () => {
    router.query = { id: '10' };
    vi.mocked(productService.getProduct).mockResolvedValue({
      id: 10,
      name: 'Galaxy S24',
      shortDescription: null,
      description: null,
      specification: null,
      sku: 'S24',
      gtin: null,
      slug: 'galaxy-s24',
      isAllowedToOrder: true,
      isPublished: true,
      isFeatured: false,
      isVisible: true,
      stockTrackingEnabled: false,
      weight: null,
      dimensionUnit: null,
      length: null,
      width: null,
      height: null,
      price: 799,
      brandId: null,
      categories: [],
      metaTitle: null,
      metaKeyword: null,
      metaDescription: null,
      thumbnailMedia: { id: 31, url: 'http://m/31.png' },
      productImageMedias: [{ id: 32, url: 'http://m/32.png' }],
      taxClassId: null,
      parentId: null,
    });
    vi.mocked(productService.getProductVariations).mockResolvedValue([]);
    vi.mocked(productService.getRelatedProductIds).mockResolvedValue([]);
    vi.mocked(productService.updateProduct).mockResolvedValue(null);
    vi.mocked(mediaService.uploadImage).mockResolvedValue({ id: 33, url: 'http://m/33.png' });

    render(<ProductEditPage />);
    expect(await screen.findByRole('img', { name: 'Image 31' })).toHaveAttribute('src', 'http://m/31.png');
    expect(screen.getByRole('img', { name: 'Image 32' })).toHaveAttribute('src', 'http://m/32.png');
    await userEvent.upload(screen.getByLabelText('Product images'), png('c.png'));
    await screen.findByRole('img', { name: 'Image 33' });
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.updateProduct).toHaveBeenCalledWith(
      10,
      expect.objectContaining({ thumbnailMediaId: 31, productImageIds: [32, 33] })
    );
  });
});

describe('Category image', () => {
  const detail = {
    id: 1,
    name: 'Phones',
    slug: 'phones',
    description: null,
    parentId: 0,
    metaKeywords: null,
    metaDescription: null,
    displayOrder: null,
    isPublish: true,
    categoryImage: { id: 5, url: 'http://m/5.png' },
  };

  it('uploads an image on create and sends its id', async () => {
    vi.mocked(categoryService.createCategory).mockResolvedValue({} as never);
    vi.mocked(mediaService.uploadImage).mockResolvedValue({ id: 9, url: 'http://m/9.png' });

    render(<CategoryCreatePage />);
    await screen.findByRole('option', { name: 'Phones' });
    await userEvent.type(screen.getByLabelText('Name'), 'Tablets');
    await userEvent.upload(screen.getByLabelText('Image'), png('t.png'));
    expect(await screen.findByRole('img', { name: 'Image 9' })).toHaveAttribute('src', 'http://m/9.png');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(categoryService.createCategory).toHaveBeenCalledWith(expect.objectContaining({ name: 'Tablets', imageId: 9 }));
  });

  it('edit keeps the existing image id', async () => {
    router.query = { id: '1' };
    vi.mocked(categoryService.getCategory).mockResolvedValue(detail);
    vi.mocked(categoryService.updateCategory).mockResolvedValue(null);

    render(<CategoryEditPage />);
    expect(await screen.findByRole('img', { name: 'Image 5' })).toHaveAttribute('src', 'http://m/5.png');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(categoryService.updateCategory).toHaveBeenCalledWith(1, expect.objectContaining({ imageId: 5 }));
  });

  it('edit with the image removed sends no image id', async () => {
    router.query = { id: '1' };
    vi.mocked(categoryService.getCategory).mockResolvedValue(detail);
    vi.mocked(categoryService.updateCategory).mockResolvedValue(null);

    render(<CategoryEditPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Remove image 5' }));
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(categoryService.updateCategory).toHaveBeenCalledWith(1, expect.objectContaining({ imageId: null }));
  });
});
