import Link from 'next/link';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import Pager from '../../../common/components/Pager';
import { useDeleteAction } from '../../../common/hooks/useDeleteAction';
import { useFetch } from '../../../common/hooks/useFetch';
import DistrictsModal from '../../../modules/location/components/DistrictsModal';
import { StateOrProvince } from '../../../modules/location/models/Location';
import { getCountries } from '../../../modules/location/services/CountryService';
import { deleteStateOrProvince, getStateOrProvincePage } from '../../../modules/location/services/StateOrProvinceService';

export default function StatesOrProvincesPage() {
  const [pageNo, setPageNo] = useState(0);
  const [countryId, setCountryId] = useState('');
  const [version, setVersion] = useState(0);
  const [districtsOf, setDistrictsOf] = useState<StateOrProvince>();
  const { data: countries } = useFetch(getCountries);
  const { data, error, loading } = useFetch(
    () => getStateOrProvincePage(pageNo, countryId ? Number(countryId) : undefined),
    [pageNo, countryId, version]
  );
  const deletion = useDeleteAction('state or province', () => setVersion((v) => v + 1));
  const countryName = (id: number) => countries?.find((c) => c.id === id)?.name ?? `#${id}`;

  return (
    <Layout title="States or provinces">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">States or provinces</h1>
        <Link href="/location/state-or-provinces/create" className="btn btn-primary">
          Create state or province
        </Link>
      </div>
      <Form.Group className="mb-3" controlId="state-filter-country" style={{ maxWidth: '24rem' }}>
        <Form.Label>Country</Form.Label>
        <Form.Select
          value={countryId}
          onChange={(e) => {
            setCountryId(e.target.value);
            setPageNo(0);
          }}
        >
          <option value="">All countries</option>
          {(countries ?? []).map((c) => (
            <option key={c.id} value={String(c.id)}>
              {c.name}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.stateOrProvinceContent.length === 0 && <p>No states or provinces found.</p>}
      {data && data.stateOrProvinceContent.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th>Code</th>
              <th>Type</th>
              <th>Country</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.stateOrProvinceContent.map((state) => (
              <tr key={state.id}>
                <td>{state.id}</td>
                <td>{state.name}</td>
                <td>{state.code}</td>
                <td>{state.type}</td>
                <td>{countries ? countryName(state.countryId) : ''}</td>
                <td className="text-end text-nowrap">
                  <Button size="sm" variant="outline-secondary" onClick={() => setDistrictsOf(state)}>
                    Districts
                  </Button>{' '}
                  <Link href={`/location/state-or-provinces/${state.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(state.name, () => deleteStateOrProvince(state.id))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="states or provinces" onChange={setPageNo} />}
      {districtsOf && <DistrictsModal state={districtsOf} onClose={() => setDistrictsOf(undefined)} />}
    </Layout>
  );
}
