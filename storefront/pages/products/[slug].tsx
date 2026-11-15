import { GetServerSideProps } from 'next';
import Layout from '../../common/components/Layout';
import { ApiError } from '../../common/services/ApiClientService';
import ProductDetails from '../../modules/catalog/components/ProductDetails';
import RelatedProducts from '../../modules/catalog/components/RelatedProducts';
import { ProductDetail } from '../../modules/catalog/models/ProductDetail';
import { ProductOptions } from '../../modules/catalog/models/ProductOptions';
import { ProductVariation } from '../../modules/catalog/models/ProductVariation';
import {
  getProductDetail,
  getProductOptionValues,
  getProductVariationsByParentId,
} from '../../modules/catalog/services/ProductService';
import { groupOptionValues } from '../../modules/catalog/utils/variation';
import ProductReviews from '../../modules/rating/components/ProductReviews';
import { ProductReviewsData } from '../../modules/rating/models/Rating';
import { getAverageStar, getRatingsByProduct, REVIEWS_PAGE_SIZE } from '../../modules/rating/services/RatingService';

type Props = {
  product: ProductDetail;
  options: ProductOptions[];
  variations: ProductVariation[];
  /** null when the rating service could not be reached: the page renders without reviews. */
  reviews: ProductReviewsData | null;
};

/** The average and the first page of reviews; any failure is swallowed so the product page never fails on it. */
async function loadReviews(productId: number): Promise<ProductReviewsData | null> {
  try {
    const [average, ratings] = await Promise.all([
      getAverageStar(productId),
      getRatingsByProduct(productId, 0, REVIEWS_PAGE_SIZE),
    ]);
    return { average, ratings };
  } catch {
    return null;
  }
}

/**
 * Server-rendered like YAS's product page. Any 4xx for the slug (unknown, unpublished, a variation, or a slug
 * the server rejects such as an encoded slash) is a real 404 page; 5xx still surface as an error page.
 */
export const getServerSideProps: GetServerSideProps<Props> = async ({ params }) => {
  let product: ProductDetail;
  try {
    product = await getProductDetail(String(params?.slug));
  } catch (error) {
    if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
      return { notFound: true };
    }
    throw error;
  }
  if (!product.hasOptions) {
    return { props: { product, options: [], variations: [], reviews: await loadReviews(product.id) } };
  }
  const [optionValues, variations, reviews] = await Promise.all([
    getProductOptionValues(product.id),
    getProductVariationsByParentId(product.id),
    loadReviews(product.id),
  ]);
  return { props: { product, options: groupOptionValues(optionValues), variations, reviews } };
};

export default function ProductPage({ product, options, variations, reviews }: Props) {
  return (
    <Layout title={product.name}>
      {/* keyed by product: client-side navigation to another product starts with fresh gallery/option/quantity state */}
      <ProductDetails key={product.id} product={product} options={options} variations={variations} />
      <ProductReviews key={`reviews-${product.id}`} productId={product.id} initial={reviews} />
      <RelatedProducts productId={product.id} />
    </Layout>
  );
}
