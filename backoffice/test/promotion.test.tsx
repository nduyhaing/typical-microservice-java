import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as brandService from '../modules/catalog/services/BrandService';
import * as categoryService from '../modules/catalog/services/CategoryService';
import * as productService from '../modules/catalog/services/ProductService';
import { PromotionDetail } from '../modules/promotion/models/Promotion';
import * as promotionService from '../modules/promotion/services/PromotionService';
import PromotionEditPage from '../pages/promotion/manager-promotion/[id]/edit';
import PromotionCreatePage from '../pages/promotion/manager-promotion/create';
import PromotionsPage from '../pages/promotion/manager-promotion/index';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/catalog/services/BrandService');
vi.mock('../modules/catalog/services/CategoryService');
vi.mock('../modules/catalog/services/ProductService');
vi.mock('../modules/promotion/services/PromotionService');

const promotion = (patch: Partial<PromotionDetail>): PromotionDetail => ({
  id: 1,
  name: 'Samsung week',
  slug: 'samsung-week',
  description: 'Ten percent off Samsung',
  couponCode: 'SAVE10',
  discountType: 'PERCENTAGE',
  discountPercentage: 10,
  discountAmount: null,
  usageType: 'LIMITED',
  usageLimit: 5,
  usageCount: 2,
  minimumOrderPurchaseAmount: 50,
  isActive: true,
  startDate: '2026-11-15T08:30:15Z',
  endDate: '2026-12-15T17:00:00Z',
  applyTo: 'BRAND',
  productIds: [],
  brandIds: [2],
  categoryIds: [],
  ...patch,
});
const page = (list: PromotionDetail[]) => ({
  promotionDetailVmList: list,
  pageNo: 0,
  pageSize: 10,
  totalElements: list.length,
  totalPages: 1,
  isLast: true,
});
const brands = [
  { id: 1, name: 'Apple', slug: 'apple', isPublish: true },
  { id: 2, name: 'Samsung', slug: 'samsung', isPublish: true },
];
const categories = [
  { id: 10, name: 'Phones', slug: 'phones', parentId: -1, categoryImage: null },
  { id: 11, name: 'Laptops', slug: 'laptops', parentId: -1, categoryImage: null },
];
const listItem = (id: number, name: string) => ({
  id,
  name,
  slug: name.toLowerCase(),
  isAllowedToOrder: true,
  isPublished: true,
  isFeatured: false,
  isVisibleIndividually: true,
  price: 100,
  createdOn: null,
  taxClassId: null,
  parentId: null,
});
const local = (value: string) => new Date(value).toISOString();

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
  vi.mocked(brandService.getBrands).mockResolvedValue(brands);
  vi.mocked(categoryService.getCategories).mockResolvedValue(categories as never);
});
afterEach(() => {
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

describe('Promotions list', () => {
  it('shows promotions, filters them and deletes with a confirmation, showing the server error', async () => {
    vi.mocked(promotionService.getPromotions).mockResolvedValue(
      page([
        promotion({}),
        promotion({ id: 2, name: 'Five off', couponCode: 'FIVE', discountType: 'FIXED', discountPercentage: null, discountAmount: 5, usageType: 'UNLIMITED', usageLimit: null, usageCount: 3, isActive: false }),
      ])
    );
    vi.mocked(promotionService.deletePromotion).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'PROMOTION_IN_USE', detail: 'Promotion 1 has been used and cannot be deleted; deactivate it instead', fieldErrors: [] })
    );
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<PromotionsPage />);

    const first = (await screen.findByText('Samsung week')).closest('tr')!;
    expect(within(first).getByText('SAVE10')).toBeInTheDocument();
    expect(within(first).getByText('10%')).toBeInTheDocument();
    expect(within(first).getByText('2 / 5')).toBeInTheDocument();
    expect(within(first).getByText('Active')).toBeInTheDocument();
    expect(within(first).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/promotion/manager-promotion/1/edit');
    const second = screen.getByText('Five off').closest('tr')!;
    expect(within(second).getByText('$5.00')).toBeInTheDocument();
    expect(within(second).getByText('3 / unlimited')).toBeInTheDocument();
    expect(within(second).getByText('Inactive')).toBeInTheDocument();
    expect(promotionService.getPromotions).toHaveBeenLastCalledWith(0, { promotionName: '', couponCode: '', startDate: '', endDate: '' });

    await userEvent.type(screen.getByLabelText('Name'), 'samsung');
    await userEvent.type(screen.getByLabelText('Coupon code'), 'SAVE');
    fireEvent.change(screen.getByLabelText('Running from'), { target: { value: '2026-11-01T00:00' } });
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(promotionService.getPromotions).toHaveBeenLastCalledWith(0, {
      promotionName: 'samsung',
      couponCode: 'SAVE',
      startDate: local('2026-11-01T00:00'),
      endDate: '',
    });

    await userEvent.click(within(screen.getByText('Samsung week').closest('tr')!).getByRole('button', { name: 'Delete' }));
    expect(window.confirm).toHaveBeenCalledWith('Delete promotion "Samsung week"?');
    expect(promotionService.deletePromotion).toHaveBeenCalledWith(1);
    expect(await screen.findByRole('alert')).toHaveTextContent('has been used and cannot be deleted');
  });
});

describe('Promotion form', () => {
  async function fillRequired() {
    await userEvent.type(screen.getByLabelText('Name'), 'Samsung week');
    await userEvent.type(screen.getByLabelText('Coupon code'), 'SAVE10');
    await userEvent.type(screen.getByLabelText('Discount value'), '10');
    fireEvent.change(screen.getByLabelText('Start'), { target: { value: '2026-11-20T09:00' } });
    fireEvent.change(screen.getByLabelText('End'), { target: { value: '2026-12-20T09:00' } });
  }

  it('creates a brand promotion with the picked brand ids', async () => {
    vi.mocked(promotionService.createPromotion).mockResolvedValue(promotion({}));
    render(<PromotionCreatePage />);
    await fillRequired();
    await userEvent.selectOptions(screen.getByLabelText('Apply to'), 'BRAND');
    await userEvent.click(await screen.findByLabelText('Samsung'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(promotionService.createPromotion).toHaveBeenCalledWith({
      name: 'Samsung week',
      slug: 'samsung-week',
      description: null,
      couponCode: 'SAVE10',
      discountType: 'PERCENTAGE',
      discountPercentage: 10,
      discountAmount: null,
      usageType: 'UNLIMITED',
      usageLimit: null,
      minimumOrderPurchaseAmount: 0,
      isActive: true,
      startDate: local('2026-11-20T09:00'),
      endDate: local('2026-12-20T09:00'),
      applyTo: 'BRAND',
      productIds: [],
      brandIds: [2],
      categoryIds: [],
    });
    expect(push).toHaveBeenCalledWith('/promotion/manager-promotion');
  });

  it('picks products by search and sends their ids', async () => {
    vi.mocked(productService.getProducts).mockResolvedValue({
      productContent: [listItem(4, 'Galaxy A55'), listItem(13, 'Galaxy Tab S9')],
      pageNo: 0,
      pageSize: 10,
      totalElements: 2,
      totalPages: 1,
      isLast: true,
    });
    vi.mocked(promotionService.createPromotion).mockResolvedValue(promotion({}));
    render(<PromotionCreatePage />);
    await fillRequired();
    await userEvent.type(screen.getByLabelText('Search products'), 'galaxy');
    await userEvent.click(screen.getByRole('button', { name: 'Find' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add Galaxy A55' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add Galaxy Tab S9' }));
    await userEvent.click(screen.getByRole('button', { name: 'Remove Galaxy Tab S9' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(productService.getProducts).toHaveBeenCalledWith(0, 'galaxy');
    expect(promotionService.createPromotion).toHaveBeenCalledWith(
      expect.objectContaining({ applyTo: 'PRODUCT', productIds: [4], brandIds: [], categoryIds: [] })
    );
  });

  it('validates the form before calling the server', async () => {
    render(<PromotionCreatePage />);
    await fillRequired();
    const value = screen.getByLabelText('Discount value');
    await userEvent.clear(value);
    await userEvent.type(value, '100.5');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(/percentage must be more than 0 and at most 100/i)).toBeInTheDocument();
    expect(screen.getByText(/pick at least one product/i)).toBeInTheDocument();

    await userEvent.clear(value);
    await userEvent.type(value, '10');
    fireEvent.change(screen.getByLabelText('End'), { target: { value: '2026-11-19T09:00' } });
    await userEvent.selectOptions(screen.getByLabelText('Usage type'), 'LIMITED');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getByText(/end must be after the start/i)).toBeInTheDocument();
    expect(screen.getByText(/usage limit must be a whole number of at least 1/i)).toBeInTheDocument();
    expect(promotionService.createPromotion).not.toHaveBeenCalled();
  });

  it('shows the server error', async () => {
    vi.mocked(promotionService.createPromotion).mockRejectedValue(
      new ApiError(400, { statusCode: '400 BAD_REQUEST', title: 'COUPON_CODE_ALREADY_EXISTED', detail: 'The coupon code SAVE10 already exists', fieldErrors: [] })
    );
    render(<PromotionCreatePage />);
    await fillRequired();
    await userEvent.selectOptions(screen.getByLabelText('Apply to'), 'CATEGORY');
    await userEvent.click(await screen.findByLabelText('Phones'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The coupon code SAVE10 already exists');
    expect(push).not.toHaveBeenCalled();
  });

  it('edits a promotion keeping every field it did not change', async () => {
    router.query = { id: '1' };
    const existing = promotion({ isActive: false, applyTo: 'PRODUCT', productIds: [4], brandIds: [] });
    vi.mocked(promotionService.getPromotion).mockResolvedValue(existing);
    vi.mocked(productService.getProduct).mockResolvedValue({ id: 4, name: 'Galaxy A55' } as never);
    vi.mocked(promotionService.updatePromotion).mockResolvedValue(existing);

    render(<PromotionEditPage />);
    expect(await screen.findByRole('button', { name: 'Remove Galaxy A55' })).toBeInTheDocument();
    const name = screen.getByLabelText('Name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Galaxy week');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(promotionService.updatePromotion).toHaveBeenCalledWith(1, {
      name: 'Galaxy week',
      slug: 'samsung-week',
      description: 'Ten percent off Samsung',
      couponCode: 'SAVE10',
      discountType: 'PERCENTAGE',
      discountPercentage: 10,
      discountAmount: null,
      usageType: 'LIMITED',
      usageLimit: 5,
      minimumOrderPurchaseAmount: 50,
      isActive: false,
      startDate: '2026-11-15T08:30:15Z',
      endDate: '2026-12-15T17:00:00Z',
      applyTo: 'PRODUCT',
      productIds: [4],
      brandIds: [],
      categoryIds: [],
    });
    expect(screen.getByText(/used 2 times/i)).toBeInTheDocument();
    expect(push).toHaveBeenCalledWith('/promotion/manager-promotion');
  });
});
