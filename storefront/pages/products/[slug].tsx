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
import SimilarProducts from '../../modules/recommendation/components/SimilarProducts';
import { SimilarProduct } from '../../modules/recommendation/models/SimilarProduct';
import { getSimilarProducts } from '../../modules/recommendation/services/RecommendationService';

/** How long the page waits for the recommendation service before rendering without similar products. */
export const SIMILAR_PRODUCTS_TIMEOUT_MS = 1500;

type Props = {
  product: ProductDetail;
  options: ProductOptions[];
  variations: ProductVariation[];
  /** null when the rating service could not be reached: the page renders without reviews. */
  reviews: ProductReviewsData | null;
  /** empty when the recommendation service is down, slow or has nothing: the section is then hidden. */
  similarProducts: SimilarProduct[];
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

/** Similar products within a short timeout; any failure (down, slow, odd answer) is an empty list. */
async function loadSimilarProducts(productId: number): Promise<SimilarProduct[]> {
  try {
    const products = await getSimilarProducts(productId, AbortSignal.timeout(SIMILAR_PRODUCTS_TIMEOUT_MS));
    return Array.isArray(products) ? products : [];
  } catch {
    return [];
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
    const [reviews, similarProducts] = await Promise.all([loadReviews(product.id), loadSimilarProducts(product.id)]);
    return { props: { product, options: [], variations: [], reviews, similarProducts } };
  }
  const [optionValues, variations, reviews, similarProducts] = await Promise.all([
    getProductOptionValues(product.id),
    getProductVariationsByParentId(product.id),
    loadReviews(product.id),
    loadSimilarProducts(product.id),
  ]);
  return { props: { product, options: groupOptionValues(optionValues), variations, reviews, similarProducts } };
};

export default function ProductPage({ product, options, variations, reviews, similarProducts }: Props) {
  return (
    <Layout title={product.name}>
      {/* keyed by product: client-side navigation to another product starts with fresh gallery/option/quantity state */}
      <ProductDetails key={product.id} product={product} options={options} variations={variations} />
      <ProductReviews key={`reviews-${product.id}`} productId={product.id} initial={reviews} />
      <SimilarProducts key={`similar-${product.id}`} products={similarProducts} />
      <RelatedProducts productId={product.id} />
    </Layout>
  );
}
