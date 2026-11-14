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

type Props = {
  product: ProductDetail;
  options: ProductOptions[];
  variations: ProductVariation[];
};

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
    return { props: { product, options: [], variations: [] } };
  }
  const [optionValues, variations] = await Promise.all([
    getProductOptionValues(product.id),
    getProductVariationsByParentId(product.id),
  ]);
  return { props: { product, options: groupOptionValues(optionValues), variations } };
};

export default function ProductPage({ product, options, variations }: Props) {
  return (
    <Layout title={product.name}>
      <ProductDetails product={product} options={options} variations={variations} />
      <RelatedProducts productId={product.id} />
    </Layout>
  );
}
