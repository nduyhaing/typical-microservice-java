import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useDeleteAction } from '../../../common/hooks/useDeleteAction';
import { useFetch } from '../../../common/hooks/useFetch';
import { formatPrice } from '../../../common/utils/format';
import { deleteProduct, getProducts } from '../../../modules/catalog/services/ProductService';

export default function ProductsPage() {
  const [pageNo, setPageNo] = useState(0);
  const [nameInput, setNameInput] = useState('');
  const [name, setName] = useState('');
  const [version, setVersion] = useState(0);
  const { data, error, loading } = useFetch(() => getProducts(pageNo, name), [pageNo, name, version]);
  const deletion = useDeleteAction('product', () => setVersion((v) => v + 1));

  function search(event: FormEvent) {
    event.preventDefault();
    setName(nameInput.trim());
    setPageNo(0);
  }

  return (
    <Layout title="Products">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Products</h1>
        <Link href="/catalog/products/create" className="btn btn-primary">
          Create product
        </Link>
      </div>
      <Form className="d-flex gap-2 mb-3" onSubmit={search} role="search">
        <Form.Label htmlFor="product-search" visuallyHidden>
          Product name
        </Form.Label>
        <Form.Control
          id="product-search"
          placeholder="Product name"
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
        />
        <Button type="submit" variant="outline-primary">
          Search
        </Button>
      </Form>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.productContent.length === 0 && <p>No products found.</p>}
      {data && data.productContent.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th>Slug</th>
              <th className="text-end">Price</th>
              <th>Published</th>
              <th>Featured</th>
              <th>Created</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.productContent.map((product) => (
              <tr key={product.id}>
                <td>{product.id}</td>
                <td>{product.name}</td>
                <td>{product.slug}</td>
                <td className="text-end">{product.price == null ? '—' : formatPrice(product.price)}</td>
                <td>{product.isPublished ? 'Yes' : 'No'}</td>
                <td>{product.isFeatured ? 'Yes' : 'No'}</td>
                <td>{product.createdOn ? new Date(product.createdOn).toLocaleDateString('en-US') : '—'}</td>
                <td className="text-end text-nowrap">
                  <Link href={`/catalog/products/${product.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(product.name, () => deleteProduct(product.id))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && (
        <div className="d-flex align-items-center gap-3">
          <Button variant="outline-secondary" size="sm" disabled={pageNo === 0} onClick={() => setPageNo((p) => p - 1)}>
            Previous
          </Button>
          <span>
            Page {data.totalPages === 0 ? 0 : data.pageNo + 1} of {data.totalPages} ({data.totalElements} products)
          </span>
          <Button variant="outline-secondary" size="sm" disabled={data.isLast} onClick={() => setPageNo((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </Layout>
  );
}
