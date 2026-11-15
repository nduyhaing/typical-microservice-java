import Link from 'next/link';
import { FormEvent, useEffect, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { slugify } from '../../../common/utils/slug';
import { getBrands } from '../../catalog/services/BrandService';
import { getCategories } from '../../catalog/services/CategoryService';
import { getProduct } from '../../catalog/services/ProductService';
import { ApplyTo, DiscountType, PromotionDetail, PromotionPayload, UsageType } from '../models/Promotion';
import { fromLocalInput, toLocalInput } from '../utils/dates';
import ProductPicker from './ProductPicker';

type Props = {
  /** The promotion being edited; every field it has is sent back unless changed. */
  initial?: PromotionDetail;
  /** Saves the promotion; a rejection is shown on the form. */
  onSubmit: (promotion: PromotionPayload) => Promise<void>;
};

type Values = {
  name: string;
  slug: string;
  description: string;
  couponCode: string;
  discountType: DiscountType;
  discountValue: string;
  usageType: UsageType;
  usageLimit: string;
  minimumOrderPurchaseAmount: string;
  isActive: boolean;
  start: string;
  end: string;
  applyTo: ApplyTo;
  productIds: number[];
  brandIds: number[];
  categoryIds: number[];
};

const MONEY = /^\d{1,17}(\.\d{1,2})?$/;
const COUPON_CODE = /^[A-Za-z0-9_-]+$/;
const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const TARGET_NOUN: Record<ApplyTo, string> = { PRODUCT: 'product', BRAND: 'brand', CATEGORY: 'category' };

function initialValues(initial?: PromotionDetail): Values {
  if (!initial) {
    return {
      name: '',
      slug: '',
      description: '',
      couponCode: '',
      discountType: 'PERCENTAGE',
      discountValue: '',
      usageType: 'UNLIMITED',
      usageLimit: '',
      minimumOrderPurchaseAmount: '0',
      isActive: true,
      start: '',
      end: '',
      applyTo: 'PRODUCT',
      productIds: [],
      brandIds: [],
      categoryIds: [],
    };
  }
  const value = initial.discountType === 'FIXED' ? initial.discountAmount : initial.discountPercentage;
  return {
    name: initial.name,
    slug: initial.slug,
    description: initial.description ?? '',
    couponCode: initial.couponCode,
    discountType: initial.discountType,
    discountValue: value == null ? '' : String(value),
    usageType: initial.usageType,
    usageLimit: initial.usageLimit == null ? '' : String(initial.usageLimit),
    minimumOrderPurchaseAmount: String(initial.minimumOrderPurchaseAmount ?? 0),
    isActive: initial.isActive,
    start: toLocalInput(initial.startDate),
    end: toLocalInput(initial.endDate),
    applyTo: initial.applyTo,
    productIds: initial.productIds,
    brandIds: initial.brandIds,
    categoryIds: initial.categoryIds,
  };
}

/** What is wrong with the values, mirroring the promotion service's checks (4-41); empty when valid. */
function validatePromotion(values: Values): string[] {
  const problems: string[] = [];
  if (!values.name.trim() || !values.slug.trim() || !values.couponCode.trim()) {
    problems.push('Name, slug and coupon code are required.');
  }
  if (values.slug.trim() && !SLUG.test(values.slug.trim())) {
    problems.push('The slug may only contain lowercase letters, digits and dashes.');
  }
  if (values.couponCode.trim() && !COUPON_CODE.test(values.couponCode.trim())) {
    problems.push('The coupon code may only contain letters, digits, "-" and "_".');
  }
  const discount = values.discountValue.trim();
  if (values.discountType === 'PERCENTAGE') {
    if (!/^\d{1,3}(\.\d{1,2})?$/.test(discount) || Number(discount) <= 0 || Number(discount) > 100) {
      problems.push('The percentage must be more than 0 and at most 100 (at most 2 decimals).');
    }
  } else if (!MONEY.test(discount) || Number(discount) <= 0) {
    problems.push('The amount must be more than 0 (at most 2 decimals).');
  }
  if (!MONEY.test(values.minimumOrderPurchaseAmount.trim() || '0')) {
    problems.push('The minimum order amount must be 0 or more (at most 2 decimals).');
  }
  if (values.usageType === 'LIMITED' && (!/^\d+$/.test(values.usageLimit.trim()) || Number(values.usageLimit) < 1)) {
    problems.push('The usage limit must be a whole number of at least 1.');
  }
  const start = fromLocalInput(values.start);
  const end = fromLocalInput(values.end);
  if (!start || !end) {
    problems.push('Start and end are required.');
  } else if (new Date(end) <= new Date(start)) {
    problems.push('The end must be after the start.');
  }
  if (targetIds(values).length === 0) {
    problems.push(`Pick at least one ${TARGET_NOUN[values.applyTo]}.`);
  }
  return problems;
}

function targetIds(values: Values): number[] {
  if (values.applyTo === 'BRAND') {
    return values.brandIds;
  }
  return values.applyTo === 'CATEGORY' ? values.categoryIds : values.productIds;
}

/** The ISO instant for a date input: the initial instant when the input still shows it (keeps its seconds). */
function instant(local: string, initialIso: string | undefined): string {
  return initialIso && toLocalInput(initialIso) === local ? initialIso : fromLocalInput(local);
}

const toggle = (ids: number[], id: number, on: boolean) => (on ? [...ids, id] : ids.filter((x) => x !== id));

/**
 * Promotion create/edit form (YAS PromotionGeneralInformation): general fields, discount, usage, dates and what it
 * applies to, with a product search picker or brand / category checkboxes from the catalog services.
 */
export default function PromotionForm({ initial, onSubmit }: Props) {
  const [values, setValues] = useState<Values>(() => initialValues(initial));
  const [slugFollowsName, setSlugFollowsName] = useState(!initial);
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<unknown>();
  const [saving, setSaving] = useState(false);
  const [productNames, setProductNames] = useState<Record<number, string>>({});
  const { data: brands, error: brandsError } = useFetch(getBrands);
  const { data: categories, error: categoriesError } = useFetch(getCategories);

  useEffect(() => {
    // names of the products an edited promotion already applies to
    let cancelled = false;
    const ids = initial?.productIds ?? [];
    void Promise.allSettled(ids.map((id) => getProduct(id))).then((results) => {
      if (cancelled) {
        return;
      }
      const names: Record<number, string> = {};
      results.forEach((result, i) => {
        names[ids[i]] = result.status === 'fulfilled' && result.value ? result.value.name : `Product #${ids[i]}`;
      });
      setProductNames((current) => ({ ...names, ...current }));
    });
    return () => {
      cancelled = true;
    };
  }, [initial]);

  const set = (patch: Partial<Values>) => setValues((v) => ({ ...v, ...patch }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = validatePromotion(values);
    setProblems(found);
    if (found.length > 0) {
      return;
    }
    const percentage = values.discountType === 'PERCENTAGE';
    const payload: PromotionPayload = {
      name: values.name.trim(),
      slug: values.slug.trim(),
      description: values.description.trim() ? values.description.trim() : null,
      couponCode: values.couponCode.trim(),
      discountType: values.discountType,
      discountPercentage: percentage ? Number(values.discountValue) : null,
      discountAmount: percentage ? null : Number(values.discountValue),
      usageType: values.usageType,
      usageLimit: values.usageType === 'LIMITED' ? Number(values.usageLimit) : null,
      minimumOrderPurchaseAmount: Number(values.minimumOrderPurchaseAmount.trim() || '0'),
      isActive: values.isActive,
      startDate: instant(values.start, initial?.startDate),
      endDate: instant(values.end, initial?.endDate),
      applyTo: values.applyTo,
      productIds: values.applyTo === 'PRODUCT' ? values.productIds : [],
      brandIds: values.applyTo === 'BRAND' ? values.brandIds : [],
      categoryIds: values.applyTo === 'CATEGORY' ? values.categoryIds : [],
    };
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit(payload);
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Form onSubmit={submit} noValidate>
      {problems.length > 0 && (
        <Alert variant="warning">
          <ul className="mb-0">
            {problems.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        </Alert>
      )}
      {error !== undefined && <ErrorMessage error={error} />}
      <Row className="g-3 mb-3">
        <Form.Group as={Col} md={6} controlId="promotion-name">
          <Form.Label>Name</Form.Label>
          <Form.Control
            value={values.name}
            maxLength={450}
            onChange={(e) =>
              set({ name: e.target.value, ...(slugFollowsName ? { slug: slugify(e.target.value) } : {}) })
            }
          />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="promotion-slug">
          <Form.Label>Slug</Form.Label>
          <Form.Control
            value={values.slug}
            maxLength={450}
            onChange={(e) => {
              setSlugFollowsName(false);
              set({ slug: e.target.value });
            }}
          />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="promotion-code">
          <Form.Label>Coupon code</Form.Label>
          <Form.Control value={values.couponCode} maxLength={100} onChange={(e) => set({ couponCode: e.target.value })} />
          <Form.Text>Customers type it at checkout; not case-sensitive.</Form.Text>
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="promotion-active">
          <Form.Label className="d-block">Status</Form.Label>
          <Form.Check
            id="promotion-active-check"
            label="Active"
            checked={values.isActive}
            onChange={(e) => set({ isActive: e.target.checked })}
          />
        </Form.Group>
        <Form.Group as={Col} md={12} controlId="promotion-description">
          <Form.Label>Description</Form.Label>
          <Form.Control
            as="textarea"
            rows={2}
            maxLength={1000}
            value={values.description}
            onChange={(e) => set({ description: e.target.value })}
          />
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="promotion-discount-type">
          <Form.Label>Discount type</Form.Label>
          <Form.Select
            value={values.discountType}
            onChange={(e) => set({ discountType: e.target.value as DiscountType })}
          >
            <option value="PERCENTAGE">Percentage</option>
            <option value="FIXED">Fixed amount</option>
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="promotion-discount-value">
          <Form.Label>Discount value</Form.Label>
          <Form.Control
            inputMode="decimal"
            value={values.discountValue}
            onChange={(e) => set({ discountValue: e.target.value })}
          />
          <Form.Text>{values.discountType === 'PERCENTAGE' ? 'Percent, more than 0 and at most 100.' : 'Amount off.'}</Form.Text>
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="promotion-minimum">
          <Form.Label>Minimum order amount</Form.Label>
          <Form.Control
            inputMode="decimal"
            value={values.minimumOrderPurchaseAmount}
            onChange={(e) => set({ minimumOrderPurchaseAmount: e.target.value })}
          />
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="promotion-usage-type">
          <Form.Label>Usage type</Form.Label>
          <Form.Select value={values.usageType} onChange={(e) => set({ usageType: e.target.value as UsageType })}>
            <option value="UNLIMITED">Unlimited</option>
            <option value="LIMITED">Limited</option>
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="promotion-usage-limit">
          <Form.Label>Usage limit</Form.Label>
          <Form.Control
            inputMode="numeric"
            value={values.usageLimit}
            disabled={values.usageType !== 'LIMITED'}
            onChange={(e) => set({ usageLimit: e.target.value })}
          />
          <Form.Text>
            Uses over all customers.{initial ? ` Used ${initial.usageCount} times so far.` : ''}
          </Form.Text>
        </Form.Group>
        <Col md={4} />
        <Form.Group as={Col} md={6} controlId="promotion-start">
          <Form.Label>Start</Form.Label>
          <Form.Control type="datetime-local" value={values.start} onChange={(e) => set({ start: e.target.value })} />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="promotion-end">
          <Form.Label>End</Form.Label>
          <Form.Control type="datetime-local" value={values.end} onChange={(e) => set({ end: e.target.value })} />
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="promotion-apply-to">
          <Form.Label>Apply to</Form.Label>
          <Form.Select value={values.applyTo} onChange={(e) => set({ applyTo: e.target.value as ApplyTo })}>
            <option value="PRODUCT">Products</option>
            <option value="BRAND">Brands</option>
            <option value="CATEGORY">Categories</option>
          </Form.Select>
          <Form.Text>A product&apos;s variations count as the product.</Form.Text>
        </Form.Group>
        <Col md={8}>
          {values.applyTo === 'PRODUCT' && (
            <ProductPicker
              selected={values.productIds}
              names={productNames}
              onAdd={(id, name) => {
                setProductNames((n) => ({ ...n, [id]: name }));
                setValues((v) => (v.productIds.includes(id) ? v : { ...v, productIds: [...v.productIds, id] }));
              }}
              onRemove={(id) => setValues((v) => ({ ...v, productIds: v.productIds.filter((x) => x !== id) }))}
            />
          )}
          {values.applyTo === 'BRAND' && (
            <fieldset>
              <legend className="form-label fs-6">Brands</legend>
              {brandsError && <ErrorMessage error={brandsError} />}
              {(brands ?? []).map((brand) => (
                <Form.Check
                  key={brand.id}
                  id={`promotion-brand-${brand.id}`}
                  label={brand.name}
                  checked={values.brandIds.includes(brand.id)}
                  onChange={(e) => setValues((v) => ({ ...v, brandIds: toggle(v.brandIds, brand.id, e.target.checked) }))}
                />
              ))}
            </fieldset>
          )}
          {values.applyTo === 'CATEGORY' && (
            <fieldset>
              <legend className="form-label fs-6">Categories</legend>
              {categoriesError && <ErrorMessage error={categoriesError} />}
              {(categories ?? []).map((category) => (
                <Form.Check
                  key={category.id}
                  id={`promotion-category-${category.id}`}
                  label={category.name}
                  checked={values.categoryIds.includes(category.id)}
                  onChange={(e) =>
                    setValues((v) => ({ ...v, categoryIds: toggle(v.categoryIds, category.id, e.target.checked) }))
                  }
                />
              ))}
            </fieldset>
          )}
        </Col>
      </Row>
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/promotion/manager-promotion" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
