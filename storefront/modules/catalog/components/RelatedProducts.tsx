import ProductGrid from '../../../common/components/ProductGrid';
import { useFetch } from '../../../common/hooks/useFetch';
import { getRelatedProductsByProductId } from '../services/ProductService';

export default function RelatedProducts({ productId }: { productId: number }) {
  const { data } = useFetch(() => getRelatedProductsByProductId(productId), [productId]);
  if (!data || data.productContent.length === 0) {
    return null;
  }
  return (
    <section className="mt-5">
      <h2 className="h4">Related products</h2>
      <ProductGrid products={data.productContent} />
    </section>
  );
}
