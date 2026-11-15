import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { TaxClassPost } from '../models/Tax';

type Props = {
  initial?: TaxClassPost;
  /** Saves the tax class; a rejection is shown on the form. */
  onSubmit: (taxClass: TaxClassPost) => Promise<void>;
};

/** Tax class create/edit form (YAS TaxClassGeneralInformation). */
export default function TaxClassForm({ initial, onSubmit }: Props) {
  const [name, setName] = useState(initial?.name ?? '');
  const [error, setError] = useState<unknown>();
  const [invalid, setInvalid] = useState(false);
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit({ name: trimmed });
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Form onSubmit={submit} noValidate>
      {invalid && <Alert variant="warning">Name is required.</Alert>}
      {error !== undefined && <ErrorMessage error={error} />}
      <Form.Group className="mb-3" controlId="tax-class-name" style={{ maxWidth: '32rem' }}>
        <Form.Label>Name</Form.Label>
        <Form.Control value={name} required maxLength={255} onChange={(e) => setName(e.target.value)} />
      </Form.Group>
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/tax/tax-classes" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
