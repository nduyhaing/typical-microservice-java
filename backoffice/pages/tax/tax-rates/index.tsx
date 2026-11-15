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
import { deleteTaxRate, getTaxRateRows } from '../../../modules/tax/services/TaxRateService';

export default function TaxRatesPage() {
  const [pageNo, setPageNo] = useState(0);
  const [version, setVersion] = useState(0);
  const { data, error, loading } = useFetch(() => getTaxRateRows(pageNo), [pageNo, version]);
  const deletion = useDeleteAction('tax rate', () => setVersion((v) => v + 1));

  return (
    <Layout title="Tax rates">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Tax rates</h1>
        <Link href="/tax/tax-rates/create" className="btn btn-primary">
          Create tax rate
        </Link>
      </div>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data?.namesUnavailable && (
        <Alert variant="warning">Country and state names could not be loaded from the location service; ids are shown.</Alert>
      )}
      {data && data.rows.length === 0 && <p>No tax rates yet.</p>}
      {data && data.rows.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Tax class</th>
              <th>Country</th>
              <th>State or province</th>
              <th>Zip code</th>
              <th className="text-end">Rate (%)</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.rows.map((rate) => (
              <tr key={rate.id}>
                <td>{rate.id}</td>
                <td>{rate.taxClassName}</td>
                <td>{rate.countryName}</td>
                <td>{rate.stateOrProvinceName ?? 'Any'}</td>
                <td>{rate.zipCode || 'Any'}</td>
                <td className="text-end">{String(rate.rate)}</td>
                <td className="text-end text-nowrap">
                  <Link href={`/tax/tax-rates/${rate.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() =>
                      deletion.remove(`${rate.taxClassName} ${rate.rate}% in ${rate.countryName}`, () => deleteTaxRate(rate.id))
                    }
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data.page} noun="tax rates" onChange={setPageNo} />}
    </Layout>
  );
}
