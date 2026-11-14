import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { slugify } from '../../../common/utils/slug';
import { ProductDetail, ProductOption, ProductPayload, ProductVariationGet } from '../models/Product';
import { getBrands } from '../services/BrandService';
import { getCategories } from '../services/CategoryService';
import { getProductOptions } from '../services/ProductOptionService';
import {
  optionsFromVariations,
  toProductOptionValueDisplays,
  toProductOptionValues,
  toVariationRow,
} from '../utils/variations';
import ProductVariations, { OptionInput, toVariationInput, VariationInput } from './ProductVariations';

/** ProductPostVm/ProductPutVm fields the form does not edit (yet): sent back unchanged so an update keeps them. */
type Kept = Pick<
  ProductPayload,
  | 'specification'
  | 'weight'
  | 'dimensionUnit'
  | 'length'
  | 'width'
  | 'height'
  | 'metaTitle'
  | 'metaKeyword'
  | 'metaDescription'
  | 'thumbnailMediaId'
  | 'productImageIds'
  | 'relatedProductIds'
  | 'taxClassId'
>;

export type ProductFormValues = {
  name: string;
  slug: string;
  sku: string;
  gtin: string;
  price: string;
  shortDescription: string;
  description: string;
  brandId: string;
  categoryIds: number[];
  isPublished: boolean;
  isFeatured: boolean;
  isAllowedToOrder: boolean;
  isVisibleIndividually: boolean;
  stockTrackingEnabled: boolean;
  options: OptionInput[];
  variations: VariationInput[];
  kept: Kept;
};

/** A new product: published, orderable and visible; YAS's dimension rule (length >= width) holds with zeros. */
export const NEW_PRODUCT: ProductFormValues = {
  name: '',
  slug: '',
  sku: '',
  gtin: '',
  price: '',
  shortDescription: '',
  description: '',
  brandId: '',
  categoryIds: [],
  isPublished: true,
  isFeatured: false,
  isAllowedToOrder: true,
  isVisibleIndividually: true,
  stockTrackingEnabled: false,
  options: [],
  variations: [],
  kept: {
    specification: '',
    weight: 0,
    dimensionUnit: 'CM',
    length: 0,
    width: 0,
    height: 0,
    metaTitle: '',
    metaKeyword: '',
    metaDescription: '',
    thumbnailMediaId: null,
    productImageIds: [],
    relatedProductIds: [],
    taxClassId: null,
  },
};

/** Form values of an existing product, its variations and related products. */
export function toFormValues(
  product: ProductDetail,
  variations: ProductVariationGet[],
  relatedProductIds: number[],
  allOptions: ProductOption[]
): ProductFormValues {
  return {
    name: product.name,
    slug: product.slug,
    sku: product.sku ?? '',
    gtin: product.gtin ?? '',
    price: product.price == null ? '' : String(product.price),
    shortDescription: product.shortDescription ?? '',
    description: product.description ?? '',
    brandId: product.brandId == null ? '' : String(product.brandId),
    categoryIds: (product.categories ?? []).map((c) => c.id),
    isPublished: Boolean(product.isPublished),
    isFeatured: Boolean(product.isFeatured),
    isAllowedToOrder: Boolean(product.isAllowedToOrder),
    isVisibleIndividually: Boolean(product.isVisible),
    stockTrackingEnabled: Boolean(product.stockTrackingEnabled),
    options: optionsFromVariations(variations, allOptions).map((o) => ({
      id: o.id,
      name: o.name,
      valuesText: o.values.join(', '),
    })),
    variations: variations.map((v) => toVariationInput(toVariationRow(v))),
    kept: {
      specification: product.specification ?? '',
      weight: product.weight ?? 0,
      dimensionUnit: product.dimensionUnit ?? 'CM',
      length: product.length ?? 0,
      width: product.width ?? 0,
      height: product.height ?? 0,
      metaTitle: product.metaTitle ?? '',
      metaKeyword: product.metaKeyword ?? '',
      metaDescription: product.metaDescription ?? '',
      thumbnailMediaId: product.thumbnailMedia?.id ?? null,
      productImageIds: (product.productImageMedias ?? []).map((m) => m.id),
      relatedProductIds,
      taxClassId: product.taxClassId,
    },
  };
}

/** The request body; a new product's variations have no `id` (ProductVariationPostVm). */
export function toPayload(values: ProductFormValues, isNew: boolean): ProductPayload {
  const variations = values.variations.map(({ id, ...variation }) => ({
    ...(isNew ? {} : { id }),
    ...variation,
    name: variation.name.trim(),
    slug: variation.slug.trim(),
    sku: variation.sku.trim(),
    price: Number(variation.price) || 0,
  }));
  const productOptionValues = toProductOptionValues(variations);
  return {
    name: values.name.trim(),
    slug: values.slug.trim(),
    sku: values.sku.trim(),
    gtin: values.gtin.trim(),
    price: Number(values.price) || 0,
    shortDescription: values.shortDescription,
    description: values.description,
    brandId: values.brandId ? Number(values.brandId) : null,
    categoryIds: values.categoryIds,
    isPublished: values.isPublished,
    isFeatured: values.isFeatured,
    isAllowedToOrder: values.isAllowedToOrder,
    isVisibleIndividually: values.isVisibleIndividually,
    stockTrackingEnabled: values.stockTrackingEnabled,
    ...values.kept,
    variations,
    productOptionValues,
    productOptionValueDisplays: toProductOptionValueDisplays(productOptionValues),
  };
}

type Props = {
  initial: ProductFormValues;
  isNew: boolean;
  /** Saves the product; a rejection is shown on the form. */
  onSubmit: (payload: ProductPayload) => Promise<void>;
};

const FLAGS = [
  ['isPublished', 'Published'],
  ['isFeatured', 'Featured'],
  ['isAllowedToOrder', 'Allowed to order'],
  ['isVisibleIndividually', 'Visible individually'],
  ['stockTrackingEnabled', 'Stock tracking'],
] as const;

/** Product create/edit form (YAS ProductGeneralInformation + CategoryMapping + ProductVariation). */
export default function ProductForm({ initial, isNew, onSubmit }: Props) {
  const [values, setValues] = useState(initial);
  const [slugFollowsName, setSlugFollowsName] = useState(isNew);
  const [createdOptions, setCreatedOptions] = useState<ProductOption[]>([]);
  const [error, setError] = useState<unknown>();
  const [invalid, setInvalid] = useState<string>();
  const [saving, setSaving] = useState(false);
  const { data: brands } = useFetch(getBrands);
  const { data: categories } = useFetch(getCategories);
  const { data: loadedOptions } = useFetch(getProductOptions);
  const options = [...(loadedOptions ?? []), ...createdOptions.filter((c) => !loadedOptions?.some((o) => o.id === c.id))];

  const set = (patch: Partial<ProductFormValues>) => setValues((v) => ({ ...v, ...patch }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!values.name.trim() || !values.slug.trim()) {
      setInvalid('Name and slug are required.');
      return;
    }
    if (Number(values.price) < 0 || values.variations.some((v) => Number(v.price) < 0)) {
      setInvalid('Prices cannot be negative.');
      return;
    }
    setInvalid(undefined);
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit(toPayload(values, isNew));
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  function toggleCategory(id: number, checked: boolean) {
    set({ categoryIds: checked ? [...values.categoryIds, id] : values.categoryIds.filter((c) => c !== id) });
  }

  return (
    <Form onSubmit={submit} noValidate>
      {invalid && <Alert variant="warning">{invalid}</Alert>}
      {error !== undefined && <ErrorMessage error={error} />}
      <Row className="g-3 mb-3">
        <Form.Group as={Col} md={6} controlId="product-name">
          <Form.Label>Name</Form.Label>
          <Form.Control
            value={values.name}
            required
            maxLength={450}
            onChange={(e) => set({ name: e.target.value, ...(slugFollowsName ? { slug: slugify(e.target.value) } : {}) })}
          />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="product-slug">
          <Form.Label>Slug</Form.Label>
          <Form.Control
            value={values.slug}
            required
            maxLength={450}
            onChange={(e) => {
              setSlugFollowsName(false);
              set({ slug: e.target.value });
            }}
          />
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="product-sku">
          <Form.Label>SKU</Form.Label>
          <Form.Control value={values.sku} onChange={(e) => set({ sku: e.target.value })} />
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="product-gtin">
          <Form.Label>GTIN</Form.Label>
          <Form.Control value={values.gtin} onChange={(e) => set({ gtin: e.target.value })} />
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="product-price">
          <Form.Label>Price</Form.Label>
          <Form.Control
            type="number"
            min={0}
            step="0.01"
            value={values.price}
            onChange={(e) => set({ price: e.target.value })}
          />
        </Form.Group>
        <Form.Group as={Col} md={12} controlId="product-short-description">
          <Form.Label>Short description</Form.Label>
          <Form.Control value={values.shortDescription} onChange={(e) => set({ shortDescription: e.target.value })} />
        </Form.Group>
        <Form.Group as={Col} md={12} controlId="product-description">
          <Form.Label>Description</Form.Label>
          <Form.Control
            as="textarea"
            rows={4}
            value={values.description}
            onChange={(e) => set({ description: e.target.value })}
          />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="product-brand">
          <Form.Label>Brand</Form.Label>
          <Form.Select value={values.brandId} onChange={(e) => set({ brandId: e.target.value })}>
            <option value="">— none —</option>
            {(brands ?? []).map((b) => (
              <option key={b.id} value={String(b.id)}>
                {b.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Col md={6}>
          <fieldset>
            <legend className="form-label fs-6">Categories</legend>
            {(categories ?? []).map((c) => (
              <Form.Check
                inline
                key={c.id}
                id={`product-category-${c.id}`}
                label={c.name}
                checked={values.categoryIds.includes(c.id)}
                onChange={(e) => toggleCategory(c.id, e.target.checked)}
              />
            ))}
          </fieldset>
        </Col>
        <Col md={12}>
          {FLAGS.map(([key, label]) => (
            <Form.Check
              inline
              key={key}
              id={`product-${key}`}
              label={label}
              checked={values[key]}
              onChange={(e) => set({ [key]: e.target.checked })}
            />
          ))}
        </Col>
      </Row>

      <ProductVariations
        options={options}
        onOptionCreated={(option) => setCreatedOptions((created) => [...created, option])}
        selected={values.options}
        onSelectedChange={(selected) => set({ options: selected })}
        variations={values.variations}
        onVariationsChange={(variations) => set({ variations })}
        product={{ name: values.name, sku: values.sku, price: Number(values.price) || 0 }}
      />

      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/catalog/products" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
