import { vi } from 'vitest';
import * as api from '../../../common/services/ApiClientService';
import { loadSampleData } from './SampleDataService';

vi.mock('../../../common/services/ApiClientService', async (original) => ({
  ...(await original<typeof api>()),
  post: vi.fn(),
}));

afterEach(() => vi.resetAllMocks());

describe('SampleDataService', () => {
  it('posts to the admin-only sampledata endpoint without a body', async () => {
    const result = { message: 'ok', inserted: { brand: 4 }, republished: 0, total: 4 };
    vi.mocked(api.post).mockResolvedValue(result);

    await expect(loadSampleData()).resolves.toEqual(result);

    expect(api.post).toHaveBeenCalledWith('/sampledata/backoffice/sampledata', undefined);
  });
});
