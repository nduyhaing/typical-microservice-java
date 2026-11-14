import Link from 'next/link';
import Badge from 'react-bootstrap/Badge';
import Spinner from 'react-bootstrap/Spinner';
import ErrorMessage from '../common/components/ErrorMessage';
import Layout from '../common/components/Layout';
import ProductGrid from '../common/components/ProductGrid';
import { useFetch } from '../common/hooks/useFetch';
import { getCategories } from '../modules/catalog/services/CategoryService';
import { getFeaturedProducts } from '../modules/catalog/services/ProductService';

/** Home page: categories and featured products, loaded in the browser as in YAS. */
export default function HomePage() {
  const categories = useFetch(getCategories);
  const featured = useFetch(() => getFeaturedProducts(0));

  return (
    <Layout>
      <section className="mb-5">
        <h1 className="h3">Shop by category</h1>
        {categories.error && <ErrorMessage />}
        <div className="d-flex flex-wrap gap-2">
          {categories.data?.map((category) => (
            <Link key={category.id} href={`/products?categorySlug=${category.slug}`}>
              <Badge bg="secondary" className="fs-6 fw-normal">
                {category.name}
              </Badge>
            </Link>
          ))}
        </div>
      </section>
      <section>
        <h2 className="h3">Featured products</h2>
        {featured.error && <ErrorMessage />}
        {featured.loading && <Spinner animation="border" role="status" aria-label="Loading" />}
        {featured.data && <ProductGrid products={featured.data.productList} />}
      </section>
    </Layout>
  );
}
