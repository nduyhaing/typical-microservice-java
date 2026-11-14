import Link from 'next/link';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useDeleteAction } from '../../../common/hooks/useDeleteAction';
import { useFetch } from '../../../common/hooks/useFetch';
import { deleteBrand, getBrands } from '../../../modules/catalog/services/BrandService';

export default function BrandsPage() {
  const [version, setVersion] = useState(0);
  const { data: brands, error, loading } = useFetch(getBrands, [version]);
  const deletion = useDeleteAction('brand', () => setVersion((v) => v + 1));

  return (
    <Layout title="Brands">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Brands</h1>
        <Link href="/catalog/brands/create" className="btn btn-primary">
          Create brand
        </Link>
      </div>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {brands && brands.length === 0 && <p>No brands yet.</p>}
      {brands && brands.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th>Slug</th>
              <th>Published</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {brands.map((brand) => (
              <tr key={brand.id}>
                <td>{brand.id}</td>
                <td>{brand.name}</td>
                <td>{brand.slug}</td>
                <td>{brand.isPublish ? 'Yes' : 'No'}</td>
                <td className="text-end text-nowrap">
                  <Link href={`/catalog/brands/${brand.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(brand.name, () => deleteBrand(brand.id))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </Layout>
  );
}
