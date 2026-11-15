import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { StateOrProvincePost } from '../models/Location';
import { getCountries } from '../services/CountryService';

type Props = {
  initial?: StateOrProvincePost;
  /** Saves the state; a rejection is shown on the form. */
  onSubmit: (state: StateOrProvincePost) => Promise<void>;
};

type Values = { name: string; code: string; type: string; countryId: string };

/**
 * State or province create/edit form (YAS StateOrProvinceGeneralInformation). The country is chosen on create only:
 * location ignores `countryId` on update (3c-7), so the select is disabled when editing.
 */
export default function StateOrProvinceForm({ initial, onSubmit }: Props) {
  const isNew = !initial;
  const [values, setValues] = useState<Values>({
    name: initial?.name ?? '',
    code: initial?.code ?? '',
    type: initial?.type ?? '',
    countryId: initial ? String(initial.countryId) : '',
  });
  const [error, setError] = useState<unknown>();
  const [invalid, setInvalid] = useState(false);
  const [saving, setSaving] = useState(false);
  const { data: countries } = useFetch(getCountries);

  const set = (patch: Partial<Values>) => setValues((v) => ({ ...v, ...patch }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    const name = values.name.trim();
    if (!name || !values.countryId) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit({
        name,
        code: values.code.trim() || null,
        type: values.type.trim() || null,
        countryId: Number(values.countryId),
      });
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Form onSubmit={submit} noValidate>
      {invalid && <Alert variant="warning">Country and name are required.</Alert>}
      {error !== undefined && <ErrorMessage error={error} />}
      <Row className="g-3 mb-3">
        <Form.Group as={Col} md={6} controlId="state-country">
          <Form.Label>Country</Form.Label>
          <Form.Select value={values.countryId} disabled={!isNew} onChange={(e) => set({ countryId: e.target.value })}>
            <option value="">— select a country —</option>
            {(countries ?? []).map((c) => (
              <option key={c.id} value={String(c.id)}>
                {c.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="state-name">
          <Form.Label>Name</Form.Label>
          <Form.Control value={values.name} required maxLength={450} onChange={(e) => set({ name: e.target.value })} />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="state-code">
          <Form.Label>Code</Form.Label>
          <Form.Control value={values.code} maxLength={255} onChange={(e) => set({ code: e.target.value })} />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="state-type">
          <Form.Label>Type</Form.Label>
          <Form.Control value={values.type} maxLength={255} onChange={(e) => set({ type: e.target.value })} />
        </Form.Group>
      </Row>
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/location/state-or-provinces" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
