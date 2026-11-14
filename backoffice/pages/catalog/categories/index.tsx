import Link from 'next/link';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useDeleteAction } from '../../../common/hooks/useDeleteAction';
import { useFetch } from '../../../common/hooks/useFetch';
import { deleteCategory, getCategories } from '../../../modules/catalog/services/CategoryService';

export default function CategoriesPage() {
  const [version, setVersion] = useState(0);
  const { data: categories, error, loading } = useFetch(getCategories, [version]);
  const deletion = useDeleteAction('category', () => setVersion((v) => v + 1));
  const nameById = new Map((categories ?? []).map((c) => [c.id, c.name]));

  return (
    <Layout title="Categories">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Categories</h1>
        <Link href="/catalog/categories/create" className="btn btn-primary">
          Create category
        </Link>
      </div>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {categories && categories.length === 0 && <p>No categories yet.</p>}
      {categories && categories.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th>Slug</th>
              <th>Parent</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {categories.map((category) => (
              <tr key={category.id}>
                <td>{category.id}</td>
                <td>{category.name}</td>
                <td>{category.slug}</td>
                {/* CategoryGetVm: parentId -1 = top level */}
                <td>{nameById.get(category.parentId) ?? '—'}</td>
                <td className="text-end text-nowrap">
                  <Link href={`/catalog/categories/${category.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(category.name, () => deleteCategory(category.id))}
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
