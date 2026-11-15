import { FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import InputGroup from 'react-bootstrap/InputGroup';
import ListGroup from 'react-bootstrap/ListGroup';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { ProductListItem } from '../../catalog/models/Product';
import { getProducts } from '../../catalog/services/ProductService';

type Props = {
  selected: number[];
  /** Known product names by id (a selected id without a name shows as "Product #id"). */
  names: Record<number, string>;
  onAdd: (id: number, name: string) => void;
  onRemove: (id: number) => void;
};

/** Search the back-office product list by name and pick products (YAS MultipleAutoComplete for products). */
export default function ProductPicker({ selected, names, onAdd, onRemove }: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ProductListItem[]>();
  const [error, setError] = useState<unknown>();

  async function search(event?: FormEvent) {
    event?.preventDefault();
    setError(undefined);
    try {
      setResults((await getProducts(0, query.trim())).productContent);
    } catch (e) {
      setError(e);
    }
  }

  return (
    <div>
      <Form.Label htmlFor="promotion-product-search">Search products</Form.Label>
      <InputGroup className="mb-2">
        <Form.Control
          id="promotion-product-search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              void search(e);
            }
          }}
        />
        <Button variant="outline-secondary" onClick={() => void search()}>
          Find
        </Button>
      </InputGroup>
      {error !== undefined && <ErrorMessage error={error} />}
      {results && results.length === 0 && <p className="text-muted">No products found.</p>}
      {results && results.length > 0 && (
        <ListGroup className="mb-2">
          {results.map((product) => (
            <ListGroup.Item key={product.id} className="d-flex justify-content-between align-items-center">
              <span>{product.name}</span>
              <Button
                size="sm"
                variant="outline-primary"
                aria-label={`Add ${product.name}`}
                disabled={selected.includes(product.id)}
                onClick={() => onAdd(product.id, product.name)}
              >
                Add
              </Button>
            </ListGroup.Item>
          ))}
        </ListGroup>
      )}
      <div className="form-label mb-1">Selected products</div>
      {selected.length === 0 && <p className="text-muted">None yet.</p>}
      <ListGroup>
        {selected.map((id) => {
          const name = names[id] ?? `Product #${id}`;
          return (
            <ListGroup.Item key={id} className="d-flex justify-content-between align-items-center">
              <span>{name}</span>
              <Button size="sm" variant="outline-danger" aria-label={`Remove ${name}`} onClick={() => onRemove(id)}>
                Remove
              </Button>
            </ListGroup.Item>
          );
        })}
      </ListGroup>
    </div>
  );
}
