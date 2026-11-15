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
import { deleteWarehouse, getWarehousePage } from '../../../modules/inventory/services/WarehouseService';

export default function WarehousesPage() {
  const [pageNo, setPageNo] = useState(0);
  const [version, setVersion] = useState(0);
  const { data, error, loading } = useFetch(() => getWarehousePage(pageNo), [pageNo, version]);
  const deletion = useDeleteAction('warehouse', () => setVersion((v) => v + 1));

  return (
    <Layout title="Warehouses">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Warehouses</h1>
        <Link href="/inventory/warehouses/create" className="btn btn-primary">
          Create warehouse
        </Link>
      </div>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.warehouseContent.length === 0 && <p>No warehouses yet.</p>}
      {data && data.warehouseContent.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.warehouseContent.map((warehouse) => (
              <tr key={warehouse.id}>
                <td>{warehouse.id}</td>
                <td>{warehouse.name}</td>
                <td className="text-end text-nowrap">
                  <Link
                    href={`/inventory/warehouse-stocks?warehouseId=${warehouse.id}`}
                    className="btn btn-sm btn-outline-secondary"
                  >
                    Stock
                  </Link>{' '}
                  <Link href={`/inventory/warehouses/${warehouse.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(warehouse.name, () => deleteWarehouse(warehouse.id))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="warehouses" onChange={setPageNo} />}
    </Layout>
  );
}
