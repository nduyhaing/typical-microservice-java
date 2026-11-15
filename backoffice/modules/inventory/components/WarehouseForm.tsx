import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import ErrorMessage from '../../../common/components/ErrorMessage';
import AddressLocationSelects, { LocationIds } from '../../location/components/AddressLocationSelects';
import { WarehouseDetail, WarehousePost } from '../models/Inventory';

type Text = 'name' | 'contactName' | 'phone' | 'addressLine1' | 'addressLine2' | 'city' | 'zipCode';
type Values = Record<Text, string> & LocationIds;

type Props = {
  initial?: WarehouseDetail;
  /** Saves the warehouse; a rejection is shown on the form. */
  onSubmit: (warehouse: WarehousePost) => Promise<void>;
};

const idText = (id: number | null | undefined) => (id == null ? '' : String(id));
const idOrNull = (value: string) => (value ? Number(value) : null);

const FIELDS: { key: Text; label: string; maxLength: number; md: number }[] = [
  { key: 'name', label: 'Name', maxLength: 450, md: 12 },
  { key: 'contactName', label: 'Contact name', maxLength: 450, md: 6 },
  { key: 'phone', label: 'Phone', maxLength: 25, md: 6 },
  { key: 'addressLine1', label: 'Address line 1', maxLength: 450, md: 6 },
  { key: 'addressLine2', label: 'Address line 2', maxLength: 450, md: 6 },
  { key: 'city', label: 'City', maxLength: 450, md: 6 },
  { key: 'zipCode', label: 'Zip code', maxLength: 25, md: 6 },
];

/** Warehouse create/edit form with its address (YAS WarehouseGeneralInformation); an update replaces every field. */
export default function WarehouseForm({ initial, onSubmit }: Props) {
  const [values, setValues] = useState<Values>({
    name: initial?.name ?? '',
    contactName: initial?.contactName ?? '',
    phone: initial?.phone ?? '',
    addressLine1: initial?.addressLine1 ?? '',
    addressLine2: initial?.addressLine2 ?? '',
    city: initial?.city ?? '',
    zipCode: initial?.zipCode ?? '',
    countryId: idText(initial?.countryId),
    stateOrProvinceId: idText(initial?.stateOrProvinceId),
    districtId: idText(initial?.districtId),
  });
  const [error, setError] = useState<unknown>();
  const [invalid, setInvalid] = useState(false);
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const text = (key: Text) => values[key].trim();
    if (!text('name') || !text('contactName') || !text('phone') || !text('addressLine1') || !values.countryId) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit({
        name: text('name'),
        contactName: text('contactName'),
        phone: text('phone'),
        addressLine1: text('addressLine1'),
        addressLine2: text('addressLine2'),
        city: text('city'),
        zipCode: text('zipCode'),
        districtId: idOrNull(values.districtId),
        stateOrProvinceId: idOrNull(values.stateOrProvinceId),
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
      {invalid && <Alert variant="warning">Name, contact name, phone, address line 1 and country are required.</Alert>}
      {error !== undefined && <ErrorMessage error={error} />}
      <Row className="g-3 mb-3">
        {FIELDS.map((field) => (
          <Form.Group key={field.key} as={Col} md={field.md} controlId={`warehouse-${field.key}`}>
            <Form.Label>{field.label}</Form.Label>
            <Form.Control
              value={values[field.key]}
              maxLength={field.maxLength}
              onChange={(e) => setValues((v) => ({ ...v, [field.key]: e.target.value }))}
            />
          </Form.Group>
        ))}
        <AddressLocationSelects
          idPrefix="warehouse"
          value={values}
          onChange={(ids) => setValues((v) => ({ ...v, ...ids }))}
        />
      </Row>
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/inventory/warehouses" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
