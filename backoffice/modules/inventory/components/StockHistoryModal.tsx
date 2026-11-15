import Modal from 'react-bootstrap/Modal';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { Stock } from '../models/Inventory';
import { getStockHistories } from '../services/StockService';

type Props = { stock: Stock; onClose: () => void };

const signed = (n: number) => (n > 0 ? `+${n}` : String(n));

/** The adjustments of one product in one warehouse, newest first as inventory returns them (YAS histories page). */
export default function StockHistoryModal({ stock, onClose }: Props) {
  const { data, error, loading } = useFetch(
    () => getStockHistories(stock.productId, stock.warehouseId),
    [stock.productId, stock.warehouseId]
  );
  return (
    <Modal show onHide={onClose} size="lg" scrollable>
      <Modal.Header closeButton>
        <Modal.Title as="h2" className="h5">
          Stock history: {stock.productName ?? `product ${stock.productId}`}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {loading && <p>Loading...</p>}
        {error && <ErrorMessage error={error} />}
        {data && data.data.length === 0 && <p className="mb-0">No adjustments yet.</p>}
        {data && data.data.length > 0 && (
          <Table size="sm" striped responsive className="mb-0">
            <thead>
              <tr>
                <th>Date</th>
                <th className="text-end">Adjustment</th>
                <th>Note</th>
                <th>By</th>
              </tr>
            </thead>
            <tbody>
              {data.data.map((h) => (
                <tr key={h.id}>
                  <td>{h.createdOn ? new Date(h.createdOn).toLocaleString('en-US') : '—'}</td>
                  <td className="text-end">{signed(h.adjustedQuantity)}</td>
                  <td>{h.note}</td>
                  <td>{h.createdBy}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Modal.Body>
    </Modal>
  );
}
