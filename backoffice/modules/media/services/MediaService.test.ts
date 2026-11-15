import { afterEach, vi } from 'vitest';
import { uploadImage } from './MediaService';

const okJson = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('uploadImage', () => {
  it('posts multipart FormData (multipartFile + caption) to /api/media/medias, then reads the URL', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(okJson({ id: 7, caption: 'phone.png', fileName: 'phone.png', mediaType: 'image/png' }))
      .mockResolvedValueOnce(
        okJson({ id: 7, caption: 'phone.png', fileName: 'phone.png', mediaType: 'image/png', url: 'http://m/7.png' })
      );
    vi.stubGlobal('fetch', fetchMock);
    const file = new File([new Uint8Array([1, 2, 3])], 'phone.png', { type: 'image/png' });

    await expect(uploadImage(file)).resolves.toEqual({ id: 7, url: 'http://m/7.png' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/media/medias');
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
    const form = init.body as FormData;
    expect((form.get('multipartFile') as File).name).toBe('phone.png');
    expect(form.get('caption')).toBe('phone.png');
    // the browser must set the multipart boundary itself
    expect(init.headers['Content-Type']).toBeUndefined();
    expect(fetchMock.mock.calls[1][0]).toBe('/api/media/medias/7');
  });
});
