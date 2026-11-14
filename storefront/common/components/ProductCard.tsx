import Image from 'next/image';
import Link from 'next/link';
import Card from 'react-bootstrap/Card';
import { ProductThumbnail } from '../../modules/catalog/models/ProductThumbnail';
import { formatPrice } from '../utils/format';
import { imageOrPlaceholder } from '../utils/image';

export default function ProductCard({ product }: { product: ProductThumbnail }) {
  return (
    <Card className="h-100">
      <Link href={`/products/${product.slug}`}>
        <Image
          src={imageOrPlaceholder(product.thumbnailUrl)}
          alt={product.name}
          width={300}
          height={300}
          unoptimized
          className="card-img-top object-fit-contain bg-light"
        />
      </Link>
      <Card.Body>
        <Card.Title as="h3" className="fs-6">
          <Link href={`/products/${product.slug}`} className="text-decoration-none">
            {product.name}
          </Link>
        </Card.Title>
        {product.price !== undefined && <Card.Text className="fw-bold">{formatPrice(product.price)}</Card.Text>}
      </Card.Body>
    </Card>
  );
}
