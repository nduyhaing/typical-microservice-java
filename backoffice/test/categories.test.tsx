import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as categoryService from '../modules/catalog/services/CategoryService';
import CategoryEditPage from '../pages/catalog/categories/[id]/edit';
import CategoryCreatePage from '../pages/catalog/categories/create';
import CategoriesPage from '../pages/catalog/categories/index';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({
  LOGOUT_URL: '/logout',
  getAuthentication: vi.fn().mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } }),
}));
vi.mock('../modules/catalog/services/CategoryService');

const phones = { id: 1, name: 'Phones', slug: 'phones', parentId: -1, categoryImage: null };
const android = { id: 2, name: 'Android', slug: 'android', parentId: 1, categoryImage: null };
const laptops = { id: 3, name: 'Laptops', slug: 'laptops', parentId: -1, categoryImage: null };

beforeEach(() => {
  router.query = {};
  vi.mocked(categoryService.getCategories).mockResolvedValue([phones, android, laptops]);
});
afterEach(() => {
  vi.mocked(categoryService.getCategories).mockReset();
  vi.mocked(categoryService.getCategory).mockReset();
  vi.mocked(categoryService.createCategory).mockReset();
  vi.mocked(categoryService.updateCategory).mockReset();
  vi.mocked(categoryService.deleteCategory).mockReset();
  push.mockReset();
  vi.restoreAllMocks();
});

describe('Categories list', () => {
  it('lists the categories with their parent', async () => {
    render(<CategoriesPage />);

    const row = (await screen.findByText('Android')).closest('tr')!;
    expect(within(row).getByText('Phones')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/catalog/categories/2/edit');
    expect(screen.getByText('Laptops').closest('tr')).toHaveTextContent('—');
  });

  it('confirms the delete and shows the server refusal for a category with children', async () => {
    vi.mocked(categoryService.deleteCategory).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'Bad Request', detail: 'Please make sure this category does not contain any children', fieldErrors: [] })
    );
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<CategoriesPage />);
    const row = (await screen.findByText('Laptops')).closest('tr')!;
    // 'phones' (the slug) only appears in the Phones row; 'Phones' is also Android's parent
    await userEvent.click(within(screen.getByText('phones').closest('tr')!).getByRole('button', { name: 'Delete' }));

    expect(confirm).toHaveBeenCalledWith('Delete category "Phones"?');
    expect(categoryService.deleteCategory).toHaveBeenCalledWith(1);
    expect(await screen.findByRole('alert')).toHaveTextContent('does not contain any children');
    expect(row).toBeInTheDocument();
  });

  it('shows a 401 message', async () => {
    vi.mocked(categoryService.getCategories).mockRejectedValue(new ApiError(401, {}));

    render(<CategoriesPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Your session has expired');
  });
});

describe('Create category', () => {
  it('posts CategoryPostVm with the chosen parent', async () => {
    vi.mocked(categoryService.createCategory).mockResolvedValue({} as never);

    render(<CategoryCreatePage />);
    await userEvent.selectOptions(await screen.findByLabelText('Parent'), 'Phones');
    await userEvent.type(screen.getByLabelText('Name'), 'Foldables');
    await userEvent.type(screen.getByLabelText('Description'), 'Folding phones');
    await userEvent.type(screen.getByLabelText('Meta keywords'), 'fold');
    await userEvent.type(screen.getByLabelText('Meta description'), 'All foldables');
    await userEvent.type(screen.getByLabelText('Display order'), '3');
    await userEvent.click(screen.getByLabelText('Published'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(categoryService.createCategory).toHaveBeenCalledWith({
      name: 'Foldables',
      slug: 'foldables',
      description: 'Folding phones',
      parentId: 1,
      metaKeywords: 'fold',
      metaDescription: 'All foldables',
      displayOrder: 3,
      isPublish: true,
      imageId: null,
    });
    expect(push).toHaveBeenCalledWith('/catalog/categories');
  });

  it('a top-level category has no parent and no display order', async () => {
    vi.mocked(categoryService.createCategory).mockResolvedValue({} as never);

    render(<CategoryCreatePage />);
    await screen.findByRole('option', { name: 'Phones' });
    await userEvent.type(screen.getByLabelText('Name'), 'Tablets');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(categoryService.createCategory).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Tablets', slug: 'tablets', parentId: null, displayOrder: null, isPublish: false })
    );
  });
});

describe('Edit category', () => {
  it('loads the category, hides itself from the parent choices and puts the values', async () => {
    router.query = { id: '2' };
    vi.mocked(categoryService.getCategory).mockResolvedValue({
      id: 2,
      name: 'Android',
      slug: 'android',
      description: 'Android phones',
      parentId: 1,
      metaKeywords: 'android',
      metaDescription: null,
      displayOrder: 1,
      isPublish: true,
      categoryImage: null,
    });
    vi.mocked(categoryService.updateCategory).mockResolvedValue(null);

    render(<CategoryEditPage />);
    expect(await screen.findByLabelText('Name')).toHaveValue('Android');
    await screen.findByRole('option', { name: 'Laptops' });
    expect(screen.getByLabelText('Parent')).toHaveValue('1');
    expect(screen.getByLabelText('Description')).toHaveValue('Android phones');
    expect(screen.queryByRole('option', { name: 'Android' })).not.toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Parent'), 'Laptops');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(categoryService.updateCategory).toHaveBeenCalledWith(2, {
      name: 'Android',
      slug: 'android',
      description: 'Android phones',
      parentId: 3,
      metaKeywords: 'android',
      metaDescription: null,
      displayOrder: 1,
      isPublish: true,
      imageId: null,
    });
  });

  it('shows the 400 detail (e.g. parent is a descendant)', async () => {
    router.query = { id: '1' };
    vi.mocked(categoryService.getCategory).mockResolvedValue({
      id: 1,
      name: 'Phones',
      slug: 'phones',
      description: null,
      parentId: 0,
      metaKeywords: null,
      metaDescription: null,
      displayOrder: null,
      isPublish: true,
      categoryImage: null,
    });
    vi.mocked(categoryService.updateCategory).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'Bad Request', detail: 'Parent category cannot be itself children', fieldErrors: [] })
    );

    render(<CategoryEditPage />);
    await screen.findByRole('option', { name: 'Android' });
    expect(screen.getByLabelText('Parent')).toHaveValue('');
    await userEvent.selectOptions(screen.getByLabelText('Parent'), 'Android');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Parent category cannot be itself children');
    expect(push).not.toHaveBeenCalled();
  });
});
