import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useFetch } from '../../../common/hooks/useFetch';
import WarehouseSelect, { useWarehouseId } from '../../../modules/inventory/components/WarehouseSelect';
import { ExistStatus, WarehouseProduct } from '../../../modules/inventory/models/Inventory';
import { addProductsToWarehouse } from '../../../modules/inventory/services/StockService';
import { getWarehouseProducts } from '../../../modules/inventory/services/WarehouseService';

const EXIST_OPTIONS: [ExistStatus, string][] = [
  ['NO', 'No'],
  ['YES', 'Yes'],
  ['ALL', 'All'],
];

const noProducts = () => Promise.resolve<WarehouseProduct[]>([]);

const canAdd = (p: WarehouseProduct) => Boolean(p.stockTrackingEnabled) && !p.existInWh;

/** Add products to a warehouse (YAS warehouse-products); only stock-tracked products can be added (3c-25). */
export default function WarehouseProductsPage() {
  const [warehouseId, setWarehouseId] = useWarehouseId();
  const [existStatus, setExistStatus] = useState<ExistStatus>('NO');
  const [nameInput, setNameInput] = useState('');
  const [skuInput, setSkuInput] = useState('');
  const [search, setSearch] = useState({ name: '', sku: '' });
  const [version, setVersion] = useState(0);
  const [selected, setSelected] = useState<number[]>([]);
  const [addError, setAddError] = useState<unknown>();
  const [notice, setNotice] = useState<string>();
  const [adding, setAdding] = useState(false);
  const { data: products, error, loading } = useFetch(
    () => (warehouseId ? getWarehouseProducts(Number(warehouseId), search.name, search.sku, existStatus) : noProducts()),
    [warehouseId, search, existStatus, version]
  );

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setSearch({ name: nameInput.trim(), sku: skuInput.trim() });
  }

  function toggle(id: number, checked: boolean) {
    setSelected((all) => (checked ? [...all, id] : all.filter((s) => s !== id)));
  }

  async function add() {
    setAddError(undefined);
    setNotice(undefined);
    setAdding(true);
    try {
      await addProductsToWarehouse(Number(warehouseId), selected);
      setNotice(`${selected.length} product(s) added to the warehouse.`);
      setSelected([]);
      setVersion((v) => v + 1);
    } catch (e) {
      setAddError(e);
    } finally {
      setAdding(false);
    }
  }

  return (
    <Layout title="Warehouse products">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Add products to a warehouse</h1>
        {warehouseId && (
          <Link href={`/inventory/warehouse-stocks?warehouseId=${warehouseId}`} className="btn btn-outline-secondary">
            Warehouse stock
          </Link>
        )}
      </div>
      <div className="d-flex flex-wrap gap-3 mb-3">
        <WarehouseSelect
          value={warehouseId}
          onChange={(id) => {
            setWarehouseId(id);
            setSelected([]);
            setNotice(undefined);
            setAddError(undefined);
          }}
        />
        <Form.Group controlId="warehouse-exist-status">
          <Form.Label>Exist in warehouse</Form.Label>
          <Form.Select value={existStatus} onChange={(e) => setExistStatus(e.target.value as ExistStatus)}>
            {EXIST_OPTIONS.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
      </div>
      {warehouseId && (
        <Form className="d-flex flex-wrap gap-2 mb-3" onSubmit={submitSearch} role="search">
          <Form.Label htmlFor="warehouse-product-name" visuallyHidden>
            Product name
          </Form.Label>
          <Form.Control
            id="warehouse-product-name"
            placeholder="Product name"
            style={{ maxWidth: '16rem' }}
            value={nameInput}
            onChange={(e) => setNameInput(e.target.value)}
          />
          <Form.Label htmlFor="warehouse-product-sku" visuallyHidden>
            Product SKU
          </Form.Label>
          <Form.Control
            id="warehouse-product-sku"
            placeholder="Product SKU"
            style={{ maxWidth: '12rem' }}
            value={skuInput}
            onChange={(e) => setSkuInput(e.target.value)}
          />
          <Button type="submit" variant="outline-primary">
            Search
          </Button>
        </Form>
      )}
      {notice && <Alert variant="success">{notice}</Alert>}
      {addError !== undefined && <ErrorMessage error={addError} />}
      {!warehouseId && <p>Choose a warehouse.</p>}
      {warehouseId && loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {warehouseId && products && products.length === 0 && <p>No products match.</p>}
      {products && products.length > 0 && (
        <>
          <Table striped hover responsive>
            <thead>
              <tr>
                <th />
                <th>Name</th>
                <th>SKU</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr key={product.id}>
                  <td>
                    <Form.Check
                      aria-label={`Select ${product.name}`}
                      disabled={!canAdd(product)}
                      checked={selected.includes(product.id)}
                      onChange={(e) => toggle(product.id, e.target.checked)}
                    />
                  </td>
                  <td>{product.name}</td>
                  <td>{product.sku}</td>
                  <td>
                    {product.existInWh ? 'In warehouse' : product.stockTrackingEnabled ? 'Not in warehouse' : 'Stock tracking off'}
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Button onClick={add} disabled={adding || selected.length === 0}>
            Add selected products
          </Button>
        </>
      )}
    </Layout>
  );
}
