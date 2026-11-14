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

  it('non-JSON error body (e.g. an HTML 400 page) still rejects with ApiError, not a parse error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad Request</html>', { status: 400 })));

    const error = await get('/product/storefront/product/a%25b').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).body).toBe('<html>Bad Request</html>');
  });
});
