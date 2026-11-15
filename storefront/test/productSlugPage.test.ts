import { GetServerSidePropsContext } from 'next';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as productService from '../modules/catalog/services/ProductService';
import * as ratingService from '../modules/rating/services/RatingService';
import { getServerSideProps } from '../pages/products/[slug]';

vi.mock('../modules/catalog/services/ProductService');
vi.mock('../modules/rating/services/RatingService');

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

  it('loads the first page of reviews and the average with the product', async () => {
    vi.mocked(productService.getProductDetail).mockResolvedValue({ id: 2, hasOptions: false } as never);
    const ratings = { ratingList: [], totalElements: 0, totalPages: 0 };
    vi.mocked(ratingService.getAverageStar).mockResolvedValue(4.5);
    vi.mocked(ratingService.getRatingsByProduct).mockResolvedValue(ratings);

    const result = await getServerSideProps(context('usb-c-cable'));

    expect(result).toMatchObject({ props: { reviews: { average: 4.5, ratings } } });
    expect(ratingService.getRatingsByProduct).toHaveBeenCalledWith(2, 0, 5);
  });

  it('rating service down: the page still renders, without reviews', async () => {
    vi.mocked(productService.getProductDetail).mockResolvedValue({ id: 2, hasOptions: false } as never);
    vi.mocked(ratingService.getAverageStar).mockRejectedValue(new ApiError(502, '<html>Bad Gateway</html>'));
    vi.mocked(ratingService.getRatingsByProduct).mockRejectedValue(new TypeError('fetch failed'));

    const result = await getServerSideProps(context('usb-c-cable'));

    expect(result).toMatchObject({ props: { product: { id: 2 }, reviews: null } });
  });
});
