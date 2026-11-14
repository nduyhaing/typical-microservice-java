import Image from 'next/image';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Table from 'react-bootstrap/Table';
import { ApiError } from '../../../common/services/ApiClientService';
import { formatPrice } from '../../../common/utils/format';
import { imageOrPlaceholder } from '../../../common/utils/image';
import { ProductDetail } from '../models/ProductDetail';
import { ProductOptions } from '../models/ProductOptions';
import { ProductVariation } from '../models/ProductVariation';
import { findVariation } from '../utils/variation';
import { redirectToLogin } from '../../auth/services/AuthenticationService';
import { addToCart } from '../../cart/services/CartService';

type Props = {
  product: ProductDetail;
  options: ProductOptions[];
  variations: ProductVariation[];
};

export default function ProductDetails({ product, options, variations }: Props) {
  const [selected, setSelected] = useState<Record<number, string>>({});
  const [quantityText, setQuantityText] = useState('1');
  const quantity = Math.max(1, Number.parseInt(quantityText, 10) || 1);
  const [cartMessage, setCartMessage] = useState<{ variant: 'success' | 'danger'; text: string }>();
  const variation = findVariation(variations, selected);
  const price = variation?.price ?? product.price;
  const image = imageOrPlaceholder(variation?.thumbnail?.url || product.thumbnailMediaUrl);
  // a product with options is bought as one of its variations (variations are products in YAS)
  const productIdToAdd = product.hasOptions ? variation?.id : product.id;

  const handleAddToCart = async () => {
    if (productIdToAdd === undefined) {
      return;
    }
    try {
      await addToCart(productIdToAdd, quantity);
      setCartMessage({ variant: 'success', text: 'Added to your cart.' });
    } catch (error) {
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        redirectToLogin();
        return;
      }
      setCartMessage({ variant: 'danger', text: 'Could not add to your cart. Please try again.' });
    }
  };

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

          {product.isAllowedToOrder && (
            <div className="d-flex align-items-end gap-2 mt-3">
              <Form.Group controlId="quantity" style={{ width: 100 }}>
                <Form.Label>Quantity</Form.Label>
                <Form.Control
                  type="number"
                  min={1}
                  value={quantityText}
                  onChange={(event) => setQuantityText(event.target.value)}
                />
              </Form.Group>
              <Button variant="primary" disabled={productIdToAdd === undefined} onClick={handleAddToCart}>
                Add to cart
              </Button>
            </div>
          )}
          {product.hasOptions && productIdToAdd === undefined && (
            <p className="small text-muted mt-2">Choose {options.map((option) => option.name).join(' and ')} first.</p>
          )}
          {cartMessage && (
            <Alert variant={cartMessage.variant} className="mt-3 py-2">
              {cartMessage.text}
            </Alert>
          )}
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
