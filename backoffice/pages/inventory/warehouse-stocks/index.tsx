import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useFetch } from '../../../common/hooks/useFetch';
import StockHistoryModal from '../../../modules/inventory/components/StockHistoryModal';
import WarehouseSelect, { useWarehouseId } from '../../../modules/inventory/components/WarehouseSelect';
import { Stock, StockAdjustment } from '../../../modules/inventory/models/Inventory';
import { adjustStocks, getStocks } from '../../../modules/inventory/services/StockService';

type Input = { quantity: string; note: string };

const noStocks = () => Promise.resolve<Stock[]>([]);

/**
 * Stock of one warehouse (YAS warehouse-stocks): adjust quantities (+/-) with a note, see each product's history.
 * The warehouse quantity is physical; the product's sellable stock (orders take from it) is shown next to it — an
 * adjustment changes both by the same amount (decisions.md 3c-60).
 */
export default function WarehouseStocksPage() {
  const [warehouseId, setWarehouseId] = useWarehouseId();
  const [nameInput, setNameInput] = useState('');
  const [skuInput, setSkuInput] = useState('');
  const [search, setSearch] = useState({ name: '', sku: '' });
  const [version, setVersion] = useState(0);
  const [inputs, setInputs] = useState<Record<number, Input>>({});
  const [invalid, setInvalid] = useState<string>();
  const [saveError, setSaveError] = useState<unknown>();
  const [notice, setNotice] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [historyOf, setHistoryOf] = useState<Stock>();
  const { data: stocks, error, loading } = useFetch(
    () => (warehouseId ? getStocks(Number(warehouseId), search.name, search.sku) : noStocks()),
    [warehouseId, search, version]
  );

  const input = (id: number): Input => inputs[id] ?? { quantity: '', note: '' };
  const setInput = (id: number, patch: Partial<Input>) =>
    setInputs((all) => ({ ...all, [id]: { ...input(id), ...patch } }));

  function resetMessages() {
    setInvalid(undefined);
    setSaveError(undefined);
    setNotice(undefined);
  }

  function chooseWarehouse(id: string) {
    setWarehouseId(id);
    setInputs({});
    resetMessages();
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setSearch({ name: nameInput.trim(), sku: skuInput.trim() });
  }

  async function save() {
    resetMessages();
    const entered = (stocks ?? []).filter((s) => input(s.id).quantity.trim() !== '');
    if (entered.some((s) => !/^-?\d+$/.test(input(s.id).quantity.trim()))) {
      setInvalid('Adjustments must be whole numbers.');
      return;
    }
    const adjustments: StockAdjustment[] = entered
      .map((s) => ({ stockId: s.id, quantity: Number(input(s.id).quantity.trim()), note: input(s.id).note.trim() }))
      .filter((a) => a.quantity !== 0);
    if (adjustments.length === 0) {
      setInvalid('Enter an adjustment for at least one product.');
      return;
    }
    setSaving(true);
    try {
      await adjustStocks(adjustments);
      setInputs({});
      setNotice('Stock quantities updated.');
      setVersion((v) => v + 1);
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Layout title="Warehouse stocks">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Warehouse stocks</h1>
        {warehouseId && (
          <Link href={`/inventory/warehouse-products?warehouseId=${warehouseId}`} className="btn btn-primary">
            Add products
          </Link>
        )}
      </div>
      <div className="mb-3">
        <WarehouseSelect value={warehouseId} onChange={chooseWarehouse} />
      </div>
      {warehouseId && (
        <Form className="d-flex flex-wrap gap-2 mb-3" onSubmit={submitSearch} role="search">
          <Form.Label htmlFor="stock-search-name" visuallyHidden>
            Product name
          </Form.Label>
          <Form.Control
            id="stock-search-name"
            placeholder="Product name"
            style={{ maxWidth: '16rem' }}
            value={nameInput}
            onChange={(e) => setNameInput(e.target.value)}
          />
          <Form.Label htmlFor="stock-search-sku" visuallyHidden>
            Product SKU
          </Form.Label>
          <Form.Control
            id="stock-search-sku"
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
      {invalid && <Alert variant="warning">{invalid}</Alert>}
      {saveError !== undefined && <ErrorMessage error={saveError} />}
      {!warehouseId && <p>Choose a warehouse to see its stock.</p>}
      {warehouseId && loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {warehouseId && stocks && stocks.length === 0 && <p>No products in this warehouse match.</p>}
      {stocks && stocks.length > 0 && (
        <>
          <Table striped hover responsive>
            <thead>
              <tr>
                <th>Name</th>
                <th>SKU</th>
                <th className="text-end">In this warehouse</th>
                <th className="text-end" title="What orders can still take: all warehouses, minus units sold since">
                  Sellable (all warehouses)
                </th>
                <th>Adjustment (+/-)</th>
                <th>Note</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {stocks.map((stock) => {
                const name = stock.productName ?? `Product ${stock.productId}`;
                return (
                  <tr key={stock.id}>
                    <td>{name}</td>
                    <td>{stock.productSku}</td>
                    <td className="text-end">{stock.quantity}</td>
                    <td className="text-end">{stock.productStockQuantity ?? '-'}</td>
                    <td style={{ maxWidth: '9rem' }}>
                      <Form.Control
                        size="sm"
                        inputMode="numeric"
                        placeholder="e.g. 10 or -2"
                        aria-label={`Adjustment for ${name}`}
                        value={input(stock.id).quantity}
                        onChange={(e) => setInput(stock.id, { quantity: e.target.value })}
                      />
                    </td>
                    <td>
                      <Form.Control
                        size="sm"
                        maxLength={450}
                        placeholder="Reason"
                        aria-label={`Note for ${name}`}
                        value={input(stock.id).note}
                        onChange={(e) => setInput(stock.id, { note: e.target.value })}
                      />
                    </td>
                    <td className="text-end">
                      <Button size="sm" variant="outline-secondary" onClick={() => setHistoryOf(stock)}>
                        History
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
          <Button onClick={save} disabled={saving}>
            Save adjustments
          </Button>
        </>
      )}
      {historyOf && <StockHistoryModal stock={historyOf} onClose={() => setHistoryOf(undefined)} />}
    </Layout>
  );
}
