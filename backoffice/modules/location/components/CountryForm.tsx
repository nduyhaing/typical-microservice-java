import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { Country, CountryPost } from '../models/Location';

type Props = {
  initial?: CountryPost;
  /** Saves the country; a rejection is shown on the form. */
  onSubmit: (country: CountryPost) => Promise<void>;
};

const EMPTY: CountryPost = {
  code2: '',
  name: '',
  code3: '',
  isBillingEnabled: false,
  isShippingEnabled: false,
  isCityEnabled: false,
  isZipCodeEnabled: false,
  isDistrictEnabled: false,
};

const FLAGS = [
  ['isBillingEnabled', 'Billing enabled'],
  ['isShippingEnabled', 'Shipping enabled'],
  ['isCityEnabled', 'City enabled'],
  ['isZipCodeEnabled', 'Zip code enabled'],
  ['isDistrictEnabled', 'District enabled'],
] as const;

/** Form values of an existing country (PUT replaces every field, so all of them are sent back). */
export function toCountryPost(country: Country): CountryPost {
  return {
    code2: country.code2,
    name: country.name,
    code3: country.code3 ?? '',
    isBillingEnabled: Boolean(country.isBillingEnabled),
    isShippingEnabled: Boolean(country.isShippingEnabled),
    isCityEnabled: Boolean(country.isCityEnabled),
    isZipCodeEnabled: Boolean(country.isZipCodeEnabled),
    isDistrictEnabled: Boolean(country.isDistrictEnabled),
  };
}

/** Country create/edit form (YAS CountryGeneralInformation). Codes are upper-cased. */
export default function CountryForm({ initial, onSubmit }: Props) {
  const [country, setCountry] = useState<CountryPost>(initial ?? EMPTY);
  const [error, setError] = useState<unknown>();
  const [invalid, setInvalid] = useState(false);
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const body: CountryPost = {
      ...country,
      name: country.name.trim(),
      code2: country.code2.trim().toUpperCase(),
      code3: (country.code3 ?? '').trim().toUpperCase() || null,
    };
    if (!body.name || !body.code2) {
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
      {invalid && <Alert variant="warning">Name and code 2 are required.</Alert>}
      {error !== undefined && <ErrorMessage error={error} />}
      <Row className="g-3 mb-3">
        <Form.Group as={Col} md={6} controlId="country-name">
          <Form.Label>Name</Form.Label>
          <Form.Control
            value={country.name}
            required
            maxLength={450}
            onChange={(e) => setCountry({ ...country, name: e.target.value })}
          />
        </Form.Group>
        <Form.Group as={Col} md={3} controlId="country-code2">
          <Form.Label>Code 2</Form.Label>
          <Form.Control
            value={country.code2}
            required
            maxLength={3}
            onChange={(e) => setCountry({ ...country, code2: e.target.value })}
          />
        </Form.Group>
        <Form.Group as={Col} md={3} controlId="country-code3">
          <Form.Label>Code 3</Form.Label>
          <Form.Control
            value={country.code3 ?? ''}
            maxLength={3}
            onChange={(e) => setCountry({ ...country, code3: e.target.value })}
          />
        </Form.Group>
      </Row>
      <div className="mb-3">
        {FLAGS.map(([key, label]) => (
          <Form.Check
            inline
            key={key}
            id={`country-${key}`}
            label={label}
            checked={country[key]}
            onChange={(e) => setCountry({ ...country, [key]: e.target.checked })}
          />
        ))}
      </div>
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/location/countries" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
