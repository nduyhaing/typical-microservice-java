import { afterEach, vi } from 'vitest';
import { ApiError, apiBaseUrl, del, get, post, postForm, put } from './ApiClientService';
import { toErrorInfo } from './errors';

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
    expect(fetchMock).toHaveBeenCalledWith('/api/product/storefront/product/galaxy-s24', expect.objectContaining({ method: 'GET' }));
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

describe('write methods', () => {
  it('post sends JSON with the POST method and returns the body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okJson({ quantity: 2 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(post('/cart/storefront/cart/items', { productId: 1, quantity: 2 })).resolves.toEqual({ quantity: 2 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/cart/storefront/cart/items');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ productId: 1, quantity: 2 });
  });

  it('put uses PUT', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okJson({}));
    vi.stubGlobal('fetch', fetchMock);

    await put('/cart/storefront/cart/items/1', { quantity: 3 });
    expect(fetchMock.mock.calls[0][1].method).toBe('PUT');
  });

  it('del uses DELETE and accepts an empty 204 body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(del('/cart/storefront/cart/items/1')).resolves.toBeNull();
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE');
  });

  it('write errors reject with ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"statusCode":"403 FORBIDDEN"}', { status: 403 })));

    const error = await post('/cart/storefront/cart/items', {}).catch((e: unknown) => e);
    expect((error as ApiError).status).toBe(403);
  });
});


describe('413 from nginx', () => {
  it('turns a non-JSON 413 (nginx HTML page) into a clear "too large" message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html><h1>413 Request Entity Too Large</h1></html>', { status: 413 }))
    );

    const error = await postForm('/media/medias', new FormData()).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(413);
    expect(toErrorInfo(error).message).toBe('File is too large (max 10 MB)');
  });

  it('keeps a JSON 413 body as it is', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"detail":"Too big"}', { status: 413 })));

    const error = await postForm('/media/medias', new FormData()).catch((e: unknown) => e);
    expect(toErrorInfo(error).message).toBe('Too big');
  });
});
