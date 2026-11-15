import ProductGrid from '../../../common/components/ProductGrid';
import { SimilarProduct } from '../models/SimilarProduct';

/** The "Similar products" strip of the product page; nothing at all when there are none (or the service is down). */
export default function SimilarProducts({ products }: { products: SimilarProduct[] }) {
  if (products.length === 0) {
    return null;
  }
  return (
    <section className="mt-5">
      <h2 className="h4">Similar products</h2>
      <ProductGrid
        products={products.map((product) => ({
          id: product.id,
          name: product.name,
          slug: product.slug,
          thumbnailUrl: product.thumbnailUrl ?? '',
          price: product.price ?? undefined,
        }))}
      />
    </section>
  );
}
