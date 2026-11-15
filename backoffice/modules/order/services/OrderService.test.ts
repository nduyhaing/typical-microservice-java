import { vi } from 'vitest';
import * as api from '../../../common/services/ApiClientService';
import { getOrder, getOrders, updateOrderStatus } from './OrderService';

vi.mock('../../../common/services/ApiClientService', async (original) => ({
  ...(await original<typeof api>()),
  get: vi.fn(),
  put: vi.fn(),
}));

afterEach(() => vi.resetAllMocks());

describe('OrderService', () => {
  it('lists orders with encoded filters, the selected statuses and whole days as the date range', async () => {
    vi.mocked(api.get).mockResolvedValue({ orderList: [], totalElements: 12, totalPages: 2 });

    const page = await getOrders(1, {
      email: 'lan@shop & co',
      productName: 'Galaxy',
      orderStatus: ['PAID', 'SHIPPING'],
      createdFrom: '2026-11-01',
      createdTo: '2026-11-09',
    });

    const url = vi.mocked(api.get).mock.calls[0][0];
    expect(url).toMatch(/^\/order\/backoffice\/orders\?/);
    const params = new URLSearchParams(url.split('?')[1]);
    expect(params.get('pageNo')).toBe('1');
    expect(params.get('pageSize')).toBe('10');
    expect(params.get('email')).toBe('lan@shop & co');
    expect(params.get('productName')).toBe('Galaxy');
    expect(params.get('orderStatus')).toBe('PAID,SHIPPING');
    expect(params.get('createdFrom')).toBe(new Date(2026, 10, 1, 0, 0, 0, 0).toISOString());
    expect(params.get('createdTo')).toBe(new Date(2026, 10, 9, 23, 59, 59, 999).toISOString());
    expect(page).toMatchObject({ pageNo: 1, pageSize: 10, totalElements: 12, totalPages: 2, isLast: true });
  });

  it('leaves empty filters out', async () => {
    vi.mocked(api.get).mockResolvedValue({ orderList: [], totalElements: 0, totalPages: 0 });

    await getOrders(0, { email: '', productName: '', orderStatus: [], createdFrom: '', createdTo: '' });

    const url = vi.mocked(api.get).mock.calls[0][0];
    expect(url).not.toContain('orderStatus');
    expect(url).not.toContain('createdFrom');
    expect(url).not.toContain('createdTo');
  });

  it('reads one order and changes its status', async () => {
    vi.mocked(api.get).mockResolvedValue({ id: 7 });
    await getOrder(7);
    expect(api.get).toHaveBeenCalledWith('/order/backoffice/orders/7');

    vi.mocked(api.put).mockResolvedValue({ id: 7 });
    await updateOrderStatus(7, { orderStatus: 'REJECT', rejectReason: 'No such address' });
    expect(api.put).toHaveBeenCalledWith('/order/backoffice/orders/7/status', {
      orderStatus: 'REJECT',
      rejectReason: 'No such address',
    });
  });
});
