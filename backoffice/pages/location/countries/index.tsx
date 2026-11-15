import Link from 'next/link';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import Pager from '../../../common/components/Pager';
import { useDeleteAction } from '../../../common/hooks/useDeleteAction';
import { useFetch } from '../../../common/hooks/useFetch';
import { deleteCountry, getCountryPage } from '../../../modules/location/services/CountryService';

const yesNo = (value: boolean | null) => (value ? 'Yes' : 'No');

export default function CountriesPage() {
  const [pageNo, setPageNo] = useState(0);
  const [version, setVersion] = useState(0);
  const { data, error, loading } = useFetch(() => getCountryPage(pageNo), [pageNo, version]);
  const deletion = useDeleteAction('country', () => setVersion((v) => v + 1));

  return (
    <Layout title="Countries">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Countries</h1>
        <Link href="/location/countries/create" className="btn btn-primary">
          Create country
        </Link>
      </div>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.countryContent.length === 0 && <p>No countries yet.</p>}
      {data && data.countryContent.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th>Code 2</th>
              <th>Code 3</th>
              <th>Billing</th>
              <th>Shipping</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.countryContent.map((country) => (
              <tr key={country.id}>
                <td>{country.id}</td>
                <td>{country.name}</td>
                <td>{country.code2}</td>
                <td>{country.code3}</td>
                <td>{yesNo(country.isBillingEnabled)}</td>
                <td>{yesNo(country.isShippingEnabled)}</td>
                <td className="text-end text-nowrap">
                  <Link href={`/location/countries/${country.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(country.name, () => deleteCountry(country.id))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="countries" onChange={setPageNo} />}
    </Layout>
  );
}
