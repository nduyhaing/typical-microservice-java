import Image from 'next/image';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Table from 'react-bootstrap/Table';
import { useFetch } from '../../../common/hooks/useFetch';
import { ApiError } from '../../../common/services/ApiClientService';
import { formatPrice } from '../../../common/utils/format';
import { imageOrPlaceholder } from '../../../common/utils/image';
import { ProductDetail } from '../models/ProductDetail';
import { ProductOptions } from '../models/ProductOptions';
import { ProductVariation } from '../models/ProductVariation';
import { getProductsByIds } from '../services/ProductService';
import { findVariation } from '../utils/variation';
import { redirectToLogin } from '../../auth/services/AuthenticationService';
import { addToCart } from '../../cart/services/CartService';

type Props = {
  product: ProductDetail;
  options: ProductOptions[];
  variations: ProductVariation[];
};

/** The product's distinct non-empty image URLs: thumbnail first, then the gallery images. */
function galleryUrls(product: ProductDetail): string[] {
  const urls = [product.thumbnailMediaUrl, ...(product.productImageMediaUrls ?? [])].filter((url): url is string =>
    Boolean(url)
  );
  return [...new Set(urls)];
}

export default function ProductDetails({ product, options, variations }: Props) {
  const [selected, setSelected] = useState<Record<number, string>>({});
  const [quantityText, setQuantityText] = useState('1');
  const quantity = Math.max(1, Number.parseInt(quantityText, 10) || 1);
  const [cartMessage, setCartMessage] = useState<{ variant: 'success' | 'danger'; text: string }>();
  const variation = findVariation(variations, selected);
  const price = variation?.price ?? product.price;
  // gallery: product thumbnail, then its images; a strip click picks one, choosing an option resets the pick
  const gallery = galleryUrls(product);
  const [pickedImage, setPickedImage] = useState<number | null>(null);
  const image = imageOrPlaceholder(
    pickedImage !== null ? gallery[pickedImage] : variation?.thumbnail?.url || gallery[0]
  );
  // a product with options is bought as one of its variations (variations are products in YAS)
  const productIdToAdd = product.hasOptions ? variation?.id : product.id;
  // a variation's stock is not in the variation list: ask list-featured for it (decisions.md 3c-24, 3c-49)
  const variationId = product.hasOptions ? variation?.id : undefined;
  const { data: variationStock } = useFetch(
    () => (variationId === undefined ? Promise.resolve(undefined) : getProductsByIds([variationId])),
    [variationId]
  );
  // true / false when known; undefined while unknown (the cart checks the stock again)
  const inStock: boolean | undefined = product.hasOptions
    ? variationStock?.find((candidate) => candidate.id === variationId)?.stockAvailable
    : product.stockAvailable;

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
      // a 400 explains itself, e.g. "Only 3 of Galaxy S24 Violet are in stock"
      const detail = error instanceof ApiError ? (error.body as { detail?: unknown } | null)?.detail : undefined;
      setCartMessage({
        variant: 'danger',
        text:
          error instanceof ApiError && error.status === 400 && typeof detail === 'string'
            ? detail
            : 'Could not add to your cart. Please try again.',
      });
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
          {gallery.length > 1 && (
            <div className="d-flex flex-wrap gap-2 mt-2">
              {gallery.map((url, index) => {
                const active = image === url;
                return (
                  <button
                    key={url}
                    type="button"
                    className={`p-0 border rounded bg-light ${active ? 'border-dark border-2' : ''}`}
                    aria-label={`Show image ${index + 1}`}
                    aria-pressed={active}
                    onClick={() => setPickedImage(index)}
                  >
                    <Image src={url} alt="" width={72} height={72} unoptimized className="rounded" />
                  </button>
                );
              })}
            </div>
          )}
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
          {inStock !== undefined && (
            <p data-testid="stock-status">
              {inStock ? <Badge bg="success">In stock</Badge> : <Badge bg="secondary">Out of stock</Badge>}
            </p>
          )}
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
                    onClick={() => {
                      setSelected({ ...selected, [option.id]: value });
                      setPickedImage(null);
                    }}
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
              <Button
                variant="primary"
                disabled={productIdToAdd === undefined || inStock === false}
                onClick={handleAddToCart}
              >
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
