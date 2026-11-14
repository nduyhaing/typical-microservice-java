import { vi } from 'vitest';
import * as api from '../../../common/services/ApiClientService';
import * as productService from '../../catalog/services/ProductService';
import { getCartLines } from './CartService';

vi.mock('../../../common/services/ApiClientService', async (original) => ({
  ...(await original<typeof api>()),
  get: vi.fn(),
}));
vi.mock('../../catalog/services/ProductService');

describe('getCartLines', () => {
  afterEach(() => vi.resetAllMocks());

  it('joins cart items with product names and prices, dropping products no longer available', async () => {
    vi.mocked(api.get).mockResolvedValue([
      { customerId: 'u', productId: 1, quantity: 2 },
      { customerId: 'u', productId: 2, quantity: 1 },
      { customerId: 'u', productId: 3, quantity: 5 },
    ]);
    vi.mocked(productService.getProductsByIds).mockResolvedValue([
      { id: 1, name: 'Galaxy S24 Violet', slug: 'galaxy-s24-violet', thumbnailUrl: '', price: 819 },
      { id: 2, name: 'USB-C Cable', slug: 'usb-c-cable', thumbnailUrl: '', price: 19 },
    ]);

    const lines = await getCartLines();

    expect(lines).toEqual([
      { productId: 1, name: 'Galaxy S24 Violet', slug: 'galaxy-s24-violet', thumbnailUrl: '', price: 819, quantity: 2 },
      { productId: 2, name: 'USB-C Cable', slug: 'usb-c-cable', thumbnailUrl: '', price: 19, quantity: 1 },
    ]);
    expect(productService.getProductsByIds).toHaveBeenCalledWith([1, 2, 3]);
  });

  it('empty cart makes no product lookup', async () => {
    vi.mocked(api.get).mockResolvedValue([]);

    await expect(getCartLines()).resolves.toEqual([]);
    expect(productService.getProductsByIds).not.toHaveBeenCalled();
  });
});
