import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { slugify } from '../../../common/utils/slug';
import { BrandPost } from '../models/Brand';

type Props = {
  initial?: BrandPost;
  /** Saves the brand; a rejection is shown on the form. */
  onSubmit: (brand: BrandPost) => Promise<void>;
};

const EMPTY: BrandPost = { name: '', slug: '', isPublish: false };

/** Brand create/edit form (YAS BrandGeneralInformation). A new brand's slug follows its name until edited. */
export default function BrandForm({ initial, onSubmit }: Props) {
  const [brand, setBrand] = useState<BrandPost>(initial ?? EMPTY);
  const [slugFollowsName, setSlugFollowsName] = useState(!initial);
  const [error, setError] = useState<unknown>();
  const [invalid, setInvalid] = useState(false);
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const body = { ...brand, name: brand.name.trim(), slug: brand.slug.trim() };
    if (!body.name || !body.slug) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit(body);
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
      <Form.Group className="mb-3" controlId="brand-name">
        <Form.Label>Name</Form.Label>
        <Form.Control
          value={brand.name}
          required
          maxLength={255}
          onChange={(e) =>
            setBrand({ ...brand, name: e.target.value, slug: slugFollowsName ? slugify(e.target.value) : brand.slug })
          }
        />
      </Form.Group>
      <Form.Group className="mb-3" controlId="brand-slug">
        <Form.Label>Slug</Form.Label>
        <Form.Control
          value={brand.slug}
          required
          maxLength={255}
          onChange={(e) => {
            setSlugFollowsName(false);
            setBrand({ ...brand, slug: e.target.value });
          }}
        />
      </Form.Group>
      <Form.Check
        className="mb-3"
        id="brand-publish"
        label="Published"
        checked={brand.isPublish}
        onChange={(e) => setBrand({ ...brand, isPublish: e.target.checked })}
      />
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/catalog/brands" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
