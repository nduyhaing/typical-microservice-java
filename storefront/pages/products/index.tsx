import Link from 'next/link';
import { useRouter } from 'next/router';
import Col from 'react-bootstrap/Col';
import ListGroup from 'react-bootstrap/ListGroup';
import Pagination from 'react-bootstrap/Pagination';
import Row from 'react-bootstrap/Row';
import Spinner from 'react-bootstrap/Spinner';
import ErrorMessage from '../../common/components/ErrorMessage';
import Layout from '../../common/components/Layout';
import ProductGrid from '../../common/components/ProductGrid';
import { useFetch } from '../../common/hooks/useFetch';
import { ProductThumbnail } from '../../modules/catalog/models/ProductThumbnail';
import { getBrands, getCategories } from '../../modules/catalog/services/CategoryService';
import { getProductByMultiParams, getProductsByBrand } from '../../modules/catalog/services/ProductService';

const PAGE_SIZE = 12;

type Listing = { products: ProductThumbnail[]; totalPages: number };

/**
 * Product list, loaded in the browser as in YAS. Category filtering and paging use /storefront/products;
 * a brand link uses the separate brand endpoint (not combined with the category filter, as in YAS).
 */
export default function ProductsPage() {
  const router = useRouter();
  const categorySlug = typeof router.query.categorySlug === 'string' ? router.query.categorySlug : '';
  const brandSlug = typeof router.query.brandSlug === 'string' ? router.query.brandSlug : '';
  const pageNo = Number(router.query.pageNo ?? 0) || 0;

  const categories = useFetch(getCategories);
  const brands = useFetch(getBrands);
  const listing = useFetch<Listing>(async () => {
    if (!router.isReady) {
      return new Promise<Listing>(() => {});
    }
    if (brandSlug) {
      return { products: await getProductsByBrand(brandSlug), totalPages: 1 };
    }
    const query = new URLSearchParams({ categorySlug, pageNo: String(pageNo), pageSize: String(PAGE_SIZE) });
    const page = await getProductByMultiParams(query.toString());
    return { products: page.productContent, totalPages: page.totalPages };
  }, [router.isReady, categorySlug, brandSlug, pageNo]);

  const pageLink = (page: number) => ({ pathname: '/products', query: { ...router.query, pageNo: page } });

  return (
    <Layout title="Products">
      <Row className="g-4">
        <Col md={3}>
          <h2 className="h6">Categories</h2>
          <ListGroup className="mb-4">
            <ListGroup.Item action as={Link} href="/products" active={!categorySlug && !brandSlug}>
              All products
            </ListGroup.Item>
            {categories.data?.map((category) => (
              <ListGroup.Item
                key={category.id}
                action
                as={Link}
                href={`/products?categorySlug=${category.slug}`}
                active={category.slug === categorySlug}
              >
                {category.name}
              </ListGroup.Item>
            ))}
          </ListGroup>
          <h2 className="h6">Brands</h2>
          <ListGroup>
            {brands.data?.map((brand) => (
              <ListGroup.Item
                key={brand.id}
                action
                as={Link}
                href={`/products?brandSlug=${brand.slug}`}
                active={brand.slug === brandSlug}
              >
                {brand.name}
              </ListGroup.Item>
            ))}
          </ListGroup>
        </Col>
        <Col md={9}>
          <h1 className="h3 mb-3">Products</h1>
          {listing.error && <ErrorMessage />}
          {listing.loading && <Spinner animation="border" role="status" aria-label="Loading" />}
          {listing.data && (
            <>
              <ProductGrid products={listing.data.products} />
              {listing.data.totalPages > 1 && (
                <Pagination className="mt-4">
                  {Array.from({ length: listing.data.totalPages }, (_, page) => (
                    <Pagination.Item
                      key={page}
                      active={page === pageNo}
                      onClick={() => router.push(pageLink(page))}
                    >
                      {page + 1}
                    </Pagination.Item>
                  ))}
                </Pagination>
              )}
            </>
          )}
        </Col>
      </Row>
    </Layout>
  );
}
