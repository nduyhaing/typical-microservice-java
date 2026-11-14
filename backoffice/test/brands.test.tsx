import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as brandService from '../modules/catalog/services/BrandService';
import BrandEditPage from '../pages/catalog/brands/[id]/edit';
import BrandCreatePage from '../pages/catalog/brands/create';
import BrandsPage from '../pages/catalog/brands/index';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({
  LOGOUT_URL: '/logout',
  getAuthentication: vi.fn().mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } }),
}));
vi.mock('../modules/catalog/services/BrandService');

const apple = { id: 1, name: 'Apple', slug: 'apple', isPublish: true };
const samsung = { id: 2, name: 'Samsung', slug: 'samsung', isPublish: false };

beforeEach(() => {
  router.query = {};
});
afterEach(() => {
  vi.mocked(brandService.getBrands).mockReset();
  vi.mocked(brandService.getBrand).mockReset();
  vi.mocked(brandService.createBrand).mockReset();
  vi.mocked(brandService.updateBrand).mockReset();
  vi.mocked(brandService.deleteBrand).mockReset();
  push.mockReset();
  vi.restoreAllMocks();
});

describe('Brands list', () => {
  it('lists the brands with their publish state and edit links', async () => {
    vi.mocked(brandService.getBrands).mockResolvedValue([apple, samsung]);

    render(<BrandsPage />);

    const row = (await screen.findByText('Apple')).closest('tr')!;
    expect(within(row).getByText('apple')).toBeInTheDocument();
    expect(within(row).getByText('Yes')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/catalog/brands/1/edit');
    expect(screen.getByText('Samsung').closest('tr')).toHaveTextContent('No');
    expect(screen.getByRole('link', { name: 'Create brand' })).toHaveAttribute('href', '/catalog/brands/create');
  });

  it('shows the empty state', async () => {
    vi.mocked(brandService.getBrands).mockResolvedValue([]);

    render(<BrandsPage />);

    expect(await screen.findByText('No brands yet.')).toBeInTheDocument();
  });

  it('shows a 403 message instead of data', async () => {
    vi.mocked(brandService.getBrands).mockRejectedValue(new ApiError(403, {}));

    render(<BrandsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('administrators only');
  });

  it('asks for confirmation before deleting and reloads the list', async () => {
    vi.mocked(brandService.getBrands).mockResolvedValueOnce([apple, samsung]).mockResolvedValueOnce([samsung]);
    vi.mocked(brandService.deleteBrand).mockResolvedValue(null);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<BrandsPage />);
    const row = (await screen.findByText('Apple')).closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: 'Delete' }));

    expect(confirm).toHaveBeenCalledWith('Delete brand "Apple"?');
    expect(brandService.deleteBrand).toHaveBeenCalledWith(1);
    expect(await screen.findByText('Brand "Apple" deleted.')).toBeInTheDocument();
    expect(screen.queryByText('Apple')).not.toBeInTheDocument();
  });

  it('does not delete when the confirmation is cancelled', async () => {
    vi.mocked(brandService.getBrands).mockResolvedValue([apple]);
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    render(<BrandsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(brandService.deleteBrand).not.toHaveBeenCalled();
  });

  it('shows the server message when the brand is in use', async () => {
    vi.mocked(brandService.getBrands).mockResolvedValue([apple]);
    vi.mocked(brandService.deleteBrand).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'Bad Request', detail: 'Please make sure brand 1 does not contain any product', fieldErrors: [] })
    );
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<BrandsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Please make sure brand 1 does not contain any product');
    expect(screen.getByText('Apple')).toBeInTheDocument();
  });
});

describe('Create brand', () => {
  it('fills the slug from the name and posts BrandPostVm', async () => {
    vi.mocked(brandService.createBrand).mockResolvedValue({ ...apple, id: 9 });

    render(<BrandCreatePage />);
    await userEvent.type(screen.getByLabelText('Name'), 'Nokia Phones');
    expect(screen.getByLabelText('Slug')).toHaveValue('nokia-phones');
    await userEvent.click(screen.getByLabelText('Published'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(brandService.createBrand).toHaveBeenCalledWith({ name: 'Nokia Phones', slug: 'nokia-phones', isPublish: true });
    expect(push).toHaveBeenCalledWith('/catalog/brands');
  });

  it('shows the 400 detail and field errors and stays on the form', async () => {
    vi.mocked(brandService.createBrand).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'Bad Request', detail: 'Name Apple is already existed', fieldErrors: ['slug: size must be between 0 and 255'] })
    );

    render(<BrandCreatePage />);
    await userEvent.type(screen.getByLabelText('Name'), 'Apple');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Name Apple is already existed');
    expect(alert).toHaveTextContent('slug: size must be between 0 and 255');
    expect(push).not.toHaveBeenCalled();
  });

  it('requires a name and a slug', async () => {
    render(<BrandCreatePage />);
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(brandService.createBrand).not.toHaveBeenCalled();
  });
});

describe('Edit brand', () => {
  it('loads the brand and puts the changed values', async () => {
    router.query = { id: '2' };
    vi.mocked(brandService.getBrand).mockResolvedValue(samsung);
    vi.mocked(brandService.updateBrand).mockResolvedValue(null);

    render(<BrandEditPage />);
    const name = await screen.findByLabelText('Name');
    expect(name).toHaveValue('Samsung');
    expect(screen.getByLabelText('Slug')).toHaveValue('samsung');
    expect(screen.getByLabelText('Published')).not.toBeChecked();
    await userEvent.clear(name);
    await userEvent.type(name, 'Samsung Electronics');
    await userEvent.click(screen.getByLabelText('Published'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(brandService.getBrand).toHaveBeenCalledWith(2);
    // editing the name keeps the existing slug (it addresses storefront pages)
    expect(brandService.updateBrand).toHaveBeenCalledWith(2, { name: 'Samsung Electronics', slug: 'samsung', isPublish: true });
    expect(push).toHaveBeenCalledWith('/catalog/brands');
  });

  it('shows the error when the brand cannot be loaded', async () => {
    router.query = { id: '99' };
    vi.mocked(brandService.getBrand).mockRejectedValue(
      new ApiError(404, { statusCode: '404 NOT_FOUND', title: 'Not Found', detail: 'Brand 99 is not found', fieldErrors: [] })
    );

    render(<BrandEditPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Brand 99 is not found');
  });
});
