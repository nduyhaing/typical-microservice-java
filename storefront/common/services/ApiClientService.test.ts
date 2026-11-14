import { afterEach, vi } from 'vitest';
import { ApiError, apiBaseUrl, get } from './ApiClientService';

const okJson = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe('apiBaseUrl', () => {
  it('is same-origin /api in the browser', () => {
    expect(apiBaseUrl()).toBe('/api');
  });

  it('uses API_BASE_PATH_SERVER on the server', () => {
    vi.stubGlobal('window', undefined);
    vi.stubEnv('API_BASE_PATH_SERVER', 'http://bff:8087/api');
    expect(apiBaseUrl()).toBe('http://bff:8087/api');
  });
});

describe('get', () => {
  it('calls the base URL plus path and returns the JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okJson({ name: 'Galaxy S24' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(get<{ name: string }>('/product/storefront/product/galaxy-s24')).resolves.toEqual({
      name: 'Galaxy S24',
    });
    expect(fetchMock).toHaveBeenCalledWith('/api/product/storefront/product/galaxy-s24', expect.anything());
  });

  it('rejects non-2xx responses with ApiError carrying status and body', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ statusCode: '404 NOT_FOUND' }), { status: 404 }))
    );

    const error = await get('/product/storefront/product/nope').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
    expect((error as ApiError).body).toEqual({ statusCode: '404 NOT_FOUND' });
  });
});
