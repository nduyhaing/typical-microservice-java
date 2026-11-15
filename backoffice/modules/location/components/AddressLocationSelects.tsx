import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import { useFetch } from '../../../common/hooks/useFetch';
import { District, StateOrProvince } from '../models/Location';
import { getCountries } from '../services/CountryService';
import { getDistricts, getStatesOrProvinces } from '../services/StateOrProvinceService';

/** Selected ids as select values ('' = none). */
export type LocationIds = { countryId: string; stateOrProvinceId: string; districtId: string };

type Props = {
  idPrefix: string;
  value: LocationIds;
  onChange: (value: LocationIds) => void;
};

const none = <T,>() => Promise.resolve<T[]>([]);

/**
 * Country → state or province → district selects from the location back office lookups. Changing the country clears
 * state and district; changing the state clears the district. State and district are optional (3c-6).
 */
export default function AddressLocationSelects({ idPrefix, value, onChange }: Props) {
  const { data: countries } = useFetch(getCountries);
  const { data: states } = useFetch(
    () => (value.countryId ? getStatesOrProvinces(Number(value.countryId)) : none<StateOrProvince>()),
    [value.countryId]
  );
  const { data: districts } = useFetch(
    () => (value.stateOrProvinceId ? getDistricts(Number(value.stateOrProvinceId)) : none<District>()),
    [value.stateOrProvinceId]
  );

  return (
    <>
      <Form.Group as={Col} md={4} controlId={`${idPrefix}-country`}>
        <Form.Label>Country</Form.Label>
        <Form.Select
          value={value.countryId}
          onChange={(e) => onChange({ countryId: e.target.value, stateOrProvinceId: '', districtId: '' })}
        >
          <option value="">— select a country —</option>
          {(countries ?? []).map((c) => (
            <option key={c.id} value={String(c.id)}>
              {c.name}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
      <Form.Group as={Col} md={4} controlId={`${idPrefix}-state`}>
        <Form.Label>State or province</Form.Label>
        <Form.Select
          value={value.stateOrProvinceId}
          disabled={!value.countryId}
          onChange={(e) => onChange({ ...value, stateOrProvinceId: e.target.value, districtId: '' })}
        >
          <option value="">— none —</option>
          {(states ?? []).map((s) => (
            <option key={s.id} value={String(s.id)}>
              {s.name}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
      <Form.Group as={Col} md={4} controlId={`${idPrefix}-district`}>
        <Form.Label>District</Form.Label>
        <Form.Select
          value={value.districtId}
          disabled={!value.stateOrProvinceId}
          onChange={(e) => onChange({ ...value, districtId: e.target.value })}
        >
          <option value="">— none —</option>
          {(districts ?? []).map((d) => (
            <option key={d.id} value={String(d.id)}>
              {d.name}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
    </>
  );
}
