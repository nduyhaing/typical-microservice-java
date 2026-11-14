import Image from 'next/image';
import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Table from 'react-bootstrap/Table';
import { formatPrice } from '../../../common/utils/format';
import { imageOrPlaceholder } from '../../../common/utils/image';
import { ProductDetail } from '../models/ProductDetail';
import { ProductOptions } from '../models/ProductOptions';
import { ProductVariation } from '../models/ProductVariation';
import { findVariation } from '../utils/variation';

type Props = {
  product: ProductDetail;
  options: ProductOptions[];
  variations: ProductVariation[];
};

export default function ProductDetails({ product, options, variations }: Props) {
  const [selected, setSelected] = useState<Record<number, string>>({});
  const variation = findVariation(variations, selected);
  const price = variation?.price ?? product.price;
  const image = imageOrPlaceholder(variation?.thumbnail?.url || product.thumbnailMediaUrl);

  return (
    <>
      <Row className="g-4">
        <Col md={5}>
          <Image
            src={image}
            alt={product.name}
            width={480}
            height={480}
            unoptimized
            className="img-fluid bg-light rounded"
          />
        </Col>
        <Col md={7}>
          <h1 className="h2">{product.name}</h1>
          {product.brandName && <p className="text-muted mb-1">{product.brandName}</p>}
          {product.productCategories.length > 0 && (
            <p className="small text-muted">{product.productCategories.join(', ')}</p>
          )}
          <p className="fs-3 fw-bold" data-testid="price">
            {formatPrice(price)}
          </p>
          {product.shortDescription && <p>{product.shortDescription}</p>}

          {options.map((option) => (
            <div key={option.id} className="mb-3">
              <h2 className="h6">{option.name}</h2>
              {option.value.map((value) => {
                const active = selected[option.id] === value;
                return (
                  <Button
                    key={value}
                    variant={active ? 'dark' : 'outline-dark'}
                    size="sm"
                    className="me-2"
                    aria-pressed={active}
                    onClick={() => setSelected({ ...selected, [option.id]: value })}
                  >
                    {value}
                  </Button>
                );
              })}
            </div>
          ))}
        </Col>
      </Row>

      {product.productAttributeGroups.length > 0 && (
        <section className="mt-5">
          <h2 className="h4">Specification</h2>
          <Table bordered size="sm">
            <tbody>
              {product.productAttributeGroups.map((group) => [
                <tr key={`group-${group.name}`} className="table-light">
                  <th colSpan={2}>{group.name}</th>
                </tr>,
                ...group.productAttributeValues.map((attribute) => (
                  <tr key={`${group.name}-${attribute.name}`}>
                    <td className="w-25">{attribute.name}</td>
                    <td>{attribute.value}</td>
                  </tr>
                )),
              ])}
            </tbody>
          </Table>
        </section>
      )}

      {product.description && (
        <section className="mt-4">
          <h2 className="h4">Description</h2>
          <p>{product.description}</p>
        </section>
      )}
    </>
  );
}
