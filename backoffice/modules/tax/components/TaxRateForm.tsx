import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { StateOrProvince } from '../../location/models/Location';
import { getCountries } from '../../location/services/CountryService';
import { getStatesOrProvinces } from '../../location/services/StateOrProvinceService';
import { TaxRatePost } from '../models/Tax';
import { getTaxClasses } from '../services/TaxClassService';

type Props = {
  initial?: TaxRatePost;
  /** Saves the rate; a rejection is shown on the form. */
  onSubmit: (rate: TaxRatePost) => Promise<void>;
};

type Values = { taxClassId: string; countryId: string; stateOrProvinceId: string; zipCode: string; rate: string };

/** 0..100 with at most 4 decimals, like tax's `@DecimalMax("100") @Digits(integer = 3, fraction = 4)` (3c-10). */
export function isValidRate(rate: string): boolean {
  return /^\d{1,3}(\.\d{1,4})?$/.test(rate.trim()) && Number(rate) <= 100;
}

const noStates = () => Promise.resolve<StateOrProvince[]>([]);

/** Tax rate create/edit form (YAS TaxRateGeneralInformation): class, country, optional state and zip code, rate. */
export default function TaxRateForm({ initial, onSubmit }: Props) {
  const [values, setValues] = useState<Values>({
    taxClassId: initial ? String(initial.taxClassId) : '',
    countryId: initial ? String(initial.countryId) : '',
    stateOrProvinceId: initial?.stateOrProvinceId == null ? '' : String(initial.stateOrProvinceId),
    zipCode: initial?.zipCode ?? '',
    rate: initial ? String(initial.rate) : '',
  });
  const [error, setError] = useState<unknown>();
  const [invalid, setInvalid] = useState(false);
  const [saving, setSaving] = useState(false);
  const { data: taxClasses } = useFetch(getTaxClasses);
  const { data: countries } = useFetch(getCountries);
  const { data: states } = useFetch(
    () => (values.countryId ? getStatesOrProvinces(Number(values.countryId)) : noStates()),
    [values.countryId]
  );

  const set = (patch: Partial<Values>) => setValues((v) => ({ ...v, ...patch }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!values.taxClassId || !values.countryId || !isValidRate(values.rate)) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit({
        rate: Number(values.rate),
        zipCode: values.zipCode.trim(),
        taxClassId: Number(values.taxClassId),
        stateOrProvinceId: values.stateOrProvinceId ? Number(values.stateOrProvinceId) : null,
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
      {invalid && (
        <Alert variant="warning">Tax class, country and a rate from 0 to 100 (at most 4 decimals) are required.</Alert>
      )}
      {error !== undefined && <ErrorMessage error={error} />}
      <Row className="g-3 mb-3">
        <Form.Group as={Col} md={6} controlId="tax-rate-class">
          <Form.Label>Tax class</Form.Label>
          <Form.Select value={values.taxClassId} onChange={(e) => set({ taxClassId: e.target.value })}>
            <option value="">— select a tax class —</option>
            {(taxClasses ?? []).map((t) => (
              <option key={t.id} value={String(t.id)}>
                {t.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="tax-rate-rate">
          <Form.Label>Rate (%)</Form.Label>
          <Form.Control
            inputMode="decimal"
            value={values.rate}
            isInvalid={invalid && !isValidRate(values.rate)}
            onChange={(e) => set({ rate: e.target.value })}
          />
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="tax-rate-country">
          <Form.Label>Country</Form.Label>
          <Form.Select
            value={values.countryId}
            onChange={(e) => set({ countryId: e.target.value, stateOrProvinceId: '' })}
          >
            <option value="">— select a country —</option>
            {(countries ?? []).map((c) => (
              <option key={c.id} value={String(c.id)}>
                {c.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="tax-rate-state">
          <Form.Label>State or province</Form.Label>
          <Form.Select value={values.stateOrProvinceId} onChange={(e) => set({ stateOrProvinceId: e.target.value })}>
            <option value="">Any</option>
            {(states ?? []).map((s) => (
              <option key={s.id} value={String(s.id)}>
                {s.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="tax-rate-zip">
          <Form.Label>Zip code</Form.Label>
          <Form.Control value={values.zipCode} maxLength={25} onChange={(e) => set({ zipCode: e.target.value })} />
          <Form.Text>Empty applies to every zip code.</Form.Text>
        </Form.Group>
      </Row>
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/tax/tax-rates" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
