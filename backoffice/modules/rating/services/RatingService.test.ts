import { vi } from 'vitest';
import * as api from '../../../common/services/ApiClientService';
import { deleteRating, getLatestRatings, getRatings } from './RatingService';

vi.mock('../../../common/services/ApiClientService', async (original) => ({
  ...(await original<typeof api>()),
  get: vi.fn(),
  del: vi.fn(),
}));

afterEach(() => vi.resetAllMocks());

describe('RatingService', () => {
  it('lists ratings with encoded filters and whole days as the date range', async () => {
    vi.mocked(api.get).mockResolvedValue({ ratingList: [], totalElements: 12, totalPages: 2 });

    const page = await getRatings(1, {
      productName: 'Galaxy S24',
      customerName: 'Lan & co',
      message: '',
      createdFrom: '2026-11-01',
      createdTo: '2026-11-09',
    });

    const url = vi.mocked(api.get).mock.calls[0][0];
    expect(url).toMatch(/^\/rating\/backoffice\/ratings\?/);
    const params = new URLSearchParams(url.split('?')[1]);
    expect(params.get('pageNo')).toBe('1');
    expect(params.get('pageSize')).toBe('10');
    expect(params.get('productName')).toBe('Galaxy S24');
    expect(params.get('customerName')).toBe('Lan & co');
    expect(params.get('message')).toBe('');
    expect(params.get('createdFrom')).toBe(new Date(2026, 10, 1, 0, 0, 0, 0).toISOString());
    expect(params.get('createdTo')).toBe(new Date(2026, 10, 9, 23, 59, 59, 999).toISOString());
    // the paging fields the Pager needs
    expect(page).toMatchObject({ pageNo: 1, pageSize: 10, totalElements: 12, totalPages: 2, isLast: true });
  });

  it('leaves empty dates out', async () => {
    vi.mocked(api.get).mockResolvedValue({ ratingList: [], totalElements: 0, totalPages: 0 });

    await getRatings(0, { productName: '', customerName: '', message: '', createdFrom: '', createdTo: '' });

    const url = vi.mocked(api.get).mock.calls[0][0];
    expect(url).not.toContain('createdFrom');
    expect(url).not.toContain('createdTo');
  });

  it('latest ratings and delete use the YAS paths', async () => {
    vi.mocked(api.get).mockResolvedValue([]);
    await getLatestRatings(5);
    expect(api.get).toHaveBeenCalledWith('/rating/backoffice/ratings/latest/5');

    await deleteRating(7);
    expect(api.del).toHaveBeenCalledWith('/rating/backoffice/ratings/7');
  });
});
