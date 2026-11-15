import { vi } from 'vitest';
import { ApiError, get } from '../../../common/services/ApiClientService';
import { getRelatedProductIds } from './ProductService';

vi.mock('../../../common/services/ApiClientService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../common/services/ApiClientService')>()),
  get: vi.fn(),
}));

afterEach(() => {
  vi.resetAllMocks();
});

describe('getRelatedProductIds', () => {
  it('reads the back office related-products endpoint', async () => {
    vi.mocked(get).mockResolvedValue([{ id: 3 }, { id: 4 }]);

    await expect(getRelatedProductIds(10)).resolves.toEqual([3, 4]);
    expect(get).toHaveBeenCalledWith('/product/backoffice/products/related-products/10');
  });

  it('fails when the lookup fails, without falling back to the storefront list (saving would drop related products)', async () => {
    const failure = new ApiError(500, null);
    vi.mocked(get).mockRejectedValue(failure);

    await expect(getRelatedProductIds(10)).rejects.toBe(failure);
    expect(get).toHaveBeenCalledTimes(1);
  });
});
