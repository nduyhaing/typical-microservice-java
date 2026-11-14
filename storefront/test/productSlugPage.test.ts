import { GetServerSidePropsContext } from 'next';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as productService from '../modules/catalog/services/ProductService';
import { getServerSideProps } from '../pages/products/[slug]';

vi.mock('../modules/catalog/services/ProductService');

const context = (slug: string) => ({ params: { slug } }) as unknown as GetServerSidePropsContext;

describe('product page getServerSideProps', () => {
  afterEach(() => vi.resetAllMocks());

  it('unknown slug returns notFound', async () => {
    vi.mocked(productService.getProductDetail).mockRejectedValue(new ApiError(404, {}));

    await expect(getServerSideProps(context('nope'))).resolves.toEqual({ notFound: true });
  });

  it('other API errors are rethrown (Next renders its error page)', async () => {
    vi.mocked(productService.getProductDetail).mockRejectedValue(new ApiError(500, {}));

    await expect(getServerSideProps(context('boom'))).rejects.toBeInstanceOf(ApiError);
  });

  it('product with options loads grouped options and variations', async () => {
    vi.mocked(productService.getProductDetail).mockResolvedValue({ id: 1, hasOptions: true } as never);
    vi.mocked(productService.getProductOptionValues).mockResolvedValue([
      { id: 1, productOptionId: 7, productOptionName: 'Color', productOptionValue: 'Black' },
      { id: 2, productOptionId: 7, productOptionName: 'Color', productOptionValue: 'Violet' },
    ]);
    vi.mocked(productService.getProductVariationsByParentId).mockResolvedValue([]);

    const result = await getServerSideProps(context('galaxy-s24'));

    expect(result).toMatchObject({
      props: { options: [{ id: 7, name: 'Color', value: ['Black', 'Violet'] }], variations: [] },
    });
  });

  it('product without options skips option and variation calls', async () => {
    vi.mocked(productService.getProductDetail).mockResolvedValue({ id: 2, hasOptions: false } as never);

    const result = await getServerSideProps(context('usb-c-cable'));

    expect(result).toMatchObject({ props: { options: [], variations: [] } });
    expect(productService.getProductVariationsByParentId).not.toHaveBeenCalled();
  });

  it('a 400 for an odd slug (e.g. an encoded slash) is a not-found page, not a 500', async () => {
    vi.mocked(productService.getProductDetail).mockRejectedValue(new ApiError(400, '<html>Bad Request</html>'));

    await expect(getServerSideProps(context('galaxy/s24'))).resolves.toEqual({ notFound: true });
  });
});
