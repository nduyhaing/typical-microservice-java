import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { slugify } from '../../../common/utils/slug';
import ImageUpload from '../../media/components/ImageUpload';
import { UploadedImage } from '../../media/services/MediaService';
import { CategoryDetail, CategoryPost } from '../models/Category';
import { getCategories } from '../services/CategoryService';

type Props = {
  /** The category being edited (absent when creating). */
  category?: CategoryDetail;
  /** Saves the category; a rejection is shown on the form. */
  onSubmit: (category: CategoryPost) => Promise<void>;
};

type Values = {
  name: string;
  slug: string;
  description: string;
  parentId: string;
  metaKeywords: string;
  metaDescription: string;
  displayOrder: string;
  isPublish: boolean;
};

function toValues(category?: CategoryDetail): Values {
  return {
    name: category?.name ?? '',
    slug: category?.slug ?? '',
    description: category?.description ?? '',
    // CategoryGetDetailVm: parentId 0 = top level
    parentId: category && category.parentId > 0 ? String(category.parentId) : '',
    metaKeywords: category?.metaKeywords ?? '',
    metaDescription: category?.metaDescription ?? '',
    displayOrder: category?.displayOrder == null ? '' : String(category.displayOrder),
    isPublish: Boolean(category?.isPublish),
  };
}

/** Empty optional text -> null, so an edit does not turn a missing value into an empty string. */
const optional = (text: string) => (text.trim() ? text.trim() : null);

/** Category create/edit form (YAS categories create/edit pages) with a parent select. */
export default function CategoryForm({ category, onSubmit }: Props) {
  const [values, setValues] = useState<Values>(() => toValues(category));
  // the current image is kept on save (YAS sent no imageId back, which cleared it)
  const [image, setImage] = useState<UploadedImage | null>(category?.categoryImage ?? null);
  const [slugFollowsName, setSlugFollowsName] = useState(!category);
  const [error, setError] = useState<unknown>();
  const [invalid, setInvalid] = useState(false);
  const [saving, setSaving] = useState(false);
  const { data: categories } = useFetch(getCategories);
  // a category cannot be its own parent (the service also refuses its descendants)
  const parents = (categories ?? []).filter((c) => c.id !== category?.id);

  const set = (patch: Partial<Values>) => setValues((v) => ({ ...v, ...patch }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    const name = values.name.trim();
    const slug = values.slug.trim();
    if (!name || !slug) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit({
        name,
        slug,
        description: optional(values.description),
        parentId: values.parentId ? Number(values.parentId) : null,
        metaKeywords: optional(values.metaKeywords),
        metaDescription: optional(values.metaDescription),
        displayOrder: values.displayOrder.trim() ? Number(values.displayOrder) : null,
        isPublish: values.isPublish,
        imageId: image?.id ?? null,
      });
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Form onSubmit={submit} noValidate>
      {invalid && <Alert variant="warning">Name and slug are required.</Alert>}
      {error !== undefined && <ErrorMessage error={error} />}
      <Form.Group className="mb-3" controlId="category-parent">
        <Form.Label>Parent</Form.Label>
        <Form.Select value={values.parentId} onChange={(e) => set({ parentId: e.target.value })}>
          <option value="">— top level —</option>
          {parents.map((c) => (
            <option key={c.id} value={String(c.id)}>
              {c.name}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
      <Form.Group className="mb-3" controlId="category-name">
        <Form.Label>Name</Form.Label>
        <Form.Control
          value={values.name}
          required
          maxLength={255}
          onChange={(e) => set({ name: e.target.value, ...(slugFollowsName ? { slug: slugify(e.target.value) } : {}) })}
        />
      </Form.Group>
      <Form.Group className="mb-3" controlId="category-slug">
        <Form.Label>Slug</Form.Label>
        <Form.Control
          value={values.slug}
          required
          maxLength={255}
          onChange={(e) => {
            setSlugFollowsName(false);
            set({ slug: e.target.value });
          }}
        />
      </Form.Group>
      <Form.Group className="mb-3" controlId="category-description">
        <Form.Label>Description</Form.Label>
        <Form.Control
          as="textarea"
          rows={3}
          maxLength={255}
          value={values.description}
          onChange={(e) => set({ description: e.target.value })}
        />
      </Form.Group>
      <Form.Group className="mb-3" controlId="category-meta-keywords">
        <Form.Label>Meta keywords</Form.Label>
        <Form.Control maxLength={255} value={values.metaKeywords} onChange={(e) => set({ metaKeywords: e.target.value })} />
      </Form.Group>
      <Form.Group className="mb-3" controlId="category-meta-description">
        <Form.Label>Meta description</Form.Label>
        <Form.Control
          maxLength={255}
          value={values.metaDescription}
          onChange={(e) => set({ metaDescription: e.target.value })}
        />
      </Form.Group>
      <Form.Group className="mb-3" controlId="category-display-order">
        <Form.Label>Display order</Form.Label>
        <Form.Control
          type="number"
          min={0}
          max={32767}
          value={values.displayOrder}
          onChange={(e) => set({ displayOrder: e.target.value })}
        />
      </Form.Group>
      <ImageUpload
        id="category-image"
        label="Image"
        images={image ? [image] : []}
        onChange={(images) => setImage(images[0] ?? null)}
      />
      <Form.Check
        className="mb-3"
        id="category-publish"
        label="Published"
        checked={values.isPublish}
        onChange={(e) => set({ isPublish: e.target.checked })}
      />
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/catalog/categories" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
