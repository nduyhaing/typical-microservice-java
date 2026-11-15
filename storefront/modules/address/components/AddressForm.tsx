import { FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { Address, AddressPayload } from '../models/Address';
import { getCountries, getDistricts, getStatesOrProvinces } from '../services/AddressService';

type Props = {
  /** The address being edited; empty for a new one. */
  initial?: Address;
  submitLabel?: string;
  onSubmit: (payload: AddressPayload) => Promise<void>;
  onCancel?: () => void;
};

type Fields = {
  contactName: string;
  phone: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  zipCode: string;
  countryId: string;
  stateOrProvinceId: string;
  districtId: string;
};

const fromAddress = (address?: Address): Fields => ({
  contactName: address?.contactName ?? '',
  phone: address?.phone ?? '',
  addressLine1: address?.addressLine1 ?? '',
  addressLine2: address?.addressLine2 ?? '',
  city: address?.city ?? '',
  zipCode: address?.zipCode ?? '',
  countryId: address?.countryId?.toString() ?? '',
  stateOrProvinceId: address?.stateOrProvinceId?.toString() ?? '',
  districtId: address?.districtId?.toString() ?? '',
});

const optionalText = (value: string) => (value.trim() ? value.trim() : null);
const optionalId = (value: string) => (value ? Number(value) : null);

/** Builds the request body; empty optional fields become null. */
export function toPayload(fields: Fields): AddressPayload {
  return {
    contactName: fields.contactName.trim(),
    phone: fields.phone.trim(),
    addressLine1: fields.addressLine1.trim(),
    addressLine2: optionalText(fields.addressLine2),
    city: optionalText(fields.city),
    zipCode: optionalText(fields.zipCode),
    countryId: Number(fields.countryId),
    stateOrProvinceId: optionalId(fields.stateOrProvinceId),
    districtId: optionalId(fields.districtId),
  };
}

/**
 * Address create/edit form (YAS address form): country → state/province → district selects loaded from the public
 * location lookups. Changing the country clears state and district; changing the state clears the district.
 */
export default function AddressForm({ initial, submitLabel = 'Save', onSubmit, onCancel }: Props) {
  const [fields, setFields] = useState<Fields>(() => fromAddress(initial));
  const [missing, setMissing] = useState(false);
  const [failed, setFailed] = useState<string>();
  const [saving, setSaving] = useState(false);

  const { data: countries, error: countriesError } = useFetch(getCountries);
  const { data: states } = useFetch(
    () => (fields.countryId ? getStatesOrProvinces(Number(fields.countryId)) : Promise.resolve([])),
    [fields.countryId]
  );
  const { data: districts } = useFetch(
    () => (fields.stateOrProvinceId ? getDistricts(Number(fields.stateOrProvinceId)) : Promise.resolve([])),
    [fields.stateOrProvinceId]
  );

  const set = (name: keyof Fields) => (event: { target: { value: string } }) =>
    setFields((current) => ({ ...current, [name]: event.target.value }));

  const changeCountry = (event: { target: { value: string } }) =>
    setFields((current) => ({ ...current, countryId: event.target.value, stateOrProvinceId: '', districtId: '' }));

  const changeState = (event: { target: { value: string } }) =>
    setFields((current) => ({ ...current, stateOrProvinceId: event.target.value, districtId: '' }));

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const payload = toPayload(fields);
    if (!payload.contactName || !payload.phone || !payload.addressLine1 || !fields.countryId) {
      setMissing(true);
      return;
    }
    setMissing(false);
    setFailed(undefined);
    setSaving(true);
    try {
      await onSubmit(payload);
    } catch (error) {
      const detail = (error as { body?: { detail?: string } })?.body?.detail;
      setFailed(typeof detail === 'string' ? detail : 'Could not save the address. Please try again later.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Form noValidate onSubmit={submit} aria-label="Address form">
      {countriesError && <ErrorMessage message="Countries could not be loaded. Please try again later." />}
      {missing && <ErrorMessage message="Please fill in contact name, phone, address and country." />}
      {failed && <ErrorMessage message={failed} />}
      <Row className="g-3">
        <Form.Group as={Col} md={6} controlId="address-contact-name">
          <Form.Label>Contact name *</Form.Label>
          <Form.Control value={fields.contactName} onChange={set('contactName')} maxLength={450} />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="address-phone">
          <Form.Label>Phone *</Form.Label>
          <Form.Control value={fields.phone} onChange={set('phone')} maxLength={25} />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="address-line1">
          <Form.Label>Address *</Form.Label>
          <Form.Control value={fields.addressLine1} onChange={set('addressLine1')} maxLength={450} />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="address-line2">
          <Form.Label>Address line 2</Form.Label>
          <Form.Control value={fields.addressLine2} onChange={set('addressLine2')} maxLength={450} />
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="address-country">
          <Form.Label>Country *</Form.Label>
          <Form.Select value={fields.countryId} onChange={changeCountry}>
            <option value="">Select a country</option>
            {countries?.map((country) => (
              <option key={country.id} value={country.id}>
                {country.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="address-state">
          <Form.Label>State or province</Form.Label>
          <Form.Select value={fields.stateOrProvinceId} onChange={changeState} disabled={!states?.length}>
            <option value="">Select a state or province</option>
            {states?.map((state) => (
              <option key={state.id} value={state.id}>
                {state.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={4} controlId="address-district">
          <Form.Label>District</Form.Label>
          <Form.Select value={fields.districtId} onChange={set('districtId')} disabled={!districts?.length}>
            <option value="">Select a district</option>
            {districts?.map((district) => (
              <option key={district.id} value={district.id}>
                {district.name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="address-city">
          <Form.Label>City</Form.Label>
          <Form.Control value={fields.city} onChange={set('city')} maxLength={450} />
        </Form.Group>
        <Form.Group as={Col} md={6} controlId="address-zip">
          <Form.Label>Zip code</Form.Label>
          <Form.Control value={fields.zipCode} onChange={set('zipCode')} maxLength={25} />
        </Form.Group>
      </Row>
      <div className="d-flex gap-2 mt-3">
        <Button type="submit" disabled={saving}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button variant="outline-secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
        )}
      </div>
    </Form>
  );
}
