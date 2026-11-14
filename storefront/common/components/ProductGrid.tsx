import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import { ProductThumbnail } from '../../modules/catalog/models/ProductThumbnail';
import ProductCard from './ProductCard';

export default function ProductGrid({ products }: { products: ProductThumbnail[] }) {
  if (products.length === 0) {
    return <p className="text-muted">No products found.</p>;
  }
  return (
    <Row xs={2} md={3} lg={4} className="g-3">
      {products.map((product) => (
        <Col key={product.id}>
          <ProductCard product={product} />
        </Col>
      ))}
    </Row>
  );
}
