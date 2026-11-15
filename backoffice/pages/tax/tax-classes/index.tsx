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
import { deleteTaxClass, getTaxClassPage } from '../../../modules/tax/services/TaxClassService';

export default function TaxClassesPage() {
  const [pageNo, setPageNo] = useState(0);
  const [version, setVersion] = useState(0);
  const { data, error, loading } = useFetch(() => getTaxClassPage(pageNo), [pageNo, version]);
  const deletion = useDeleteAction('tax class', () => setVersion((v) => v + 1));

  return (
    <Layout title="Tax classes">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Tax classes</h1>
        <Link href="/tax/tax-classes/create" className="btn btn-primary">
          Create tax class
        </Link>
      </div>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.taxClassContent.length === 0 && <p>No tax classes yet.</p>}
      {data && data.taxClassContent.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.taxClassContent.map((taxClass) => (
              <tr key={taxClass.id}>
                <td>{taxClass.id}</td>
                <td>{taxClass.name}</td>
                <td className="text-end text-nowrap">
                  <Link href={`/tax/tax-classes/${taxClass.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(taxClass.name, () => deleteTaxClass(taxClass.id))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="tax classes" onChange={setPageNo} />}
    </Layout>
  );
}
