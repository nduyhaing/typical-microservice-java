import { vi } from 'vitest';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { PromotionPayload } from '../models/Promotion';
import {
  createPromotion,
  deletePromotion,
  getPromotion,
  getPromotions,
  updatePromotion,
} from './PromotionService';

vi.mock('../../../common/services/ApiClientService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../common/services/ApiClientService')>()),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}));

afterEach(() => vi.resetAllMocks());

const payload: PromotionPayload = {
  name: 'Save 10',
  slug: 'save-10',
  description: null,
  couponCode: 'SAVE10',
  discountType: 'PERCENTAGE',
  discountPercentage: 10,
  discountAmount: null,
  usageType: 'UNLIMITED',
  usageLimit: null,
  minimumOrderPurchaseAmount: 0,
  isActive: true,
  startDate: '2026-11-15T00:00:00.000Z',
  endDate: '2026-12-15T00:00:00.000Z',
  applyTo: 'BRAND',
  productIds: [],
  brandIds: [2],
  categoryIds: [],
};

describe('PromotionService', () => {
  it('lists with encoded filters, bounded page size, and adds isLast for the pager', async () => {
    vi.mocked(get).mockResolvedValue({ promotionDetailVmList: [], pageNo: 1, pageSize: 10, totalElements: 15, totalPages: 2 });

    const page = await getPromotions(1, { promotionName: 'Black & white', couponCode: '', startDate: '', endDate: '' });
    await getPromotions(0, {
      promotionName: '',
      couponCode: 'S10',
      startDate: '2026-11-01T00:00:00.000Z',
      endDate: '2026-11-30T00:00:00.000Z',
    });

    expect(get).toHaveBeenNthCalledWith(
      1,
      '/promotion/backoffice/promotions?pageNo=1&pageSize=10&promotionName=Black%20%26%20white&couponCode='
    );
    expect(get).toHaveBeenNthCalledWith(
      2,
      '/promotion/backoffice/promotions?pageNo=0&pageSize=10&promotionName=&couponCode=S10' +
        '&startDate=2026-11-01T00%3A00%3A00.000Z&endDate=2026-11-30T00%3A00%3A00.000Z'
    );
    expect(page.isLast).toBe(true);
  });

  it('calls the CRUD endpoints, with the id in the body on update (YAS)', async () => {
    await getPromotion(4);
    await createPromotion(payload);
    await updatePromotion(4, payload);
    await deletePromotion(4);

    expect(get).toHaveBeenCalledWith('/promotion/backoffice/promotions/4');
    expect(post).toHaveBeenCalledWith('/promotion/backoffice/promotions', payload);
    expect(put).toHaveBeenCalledWith('/promotion/backoffice/promotions', { id: 4, ...payload });
    expect(del).toHaveBeenCalledWith('/promotion/backoffice/promotions/4');
  });
});
