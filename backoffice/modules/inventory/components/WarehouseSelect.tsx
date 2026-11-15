import { useRouter } from 'next/router';
import { useState } from 'react';
import Form from 'react-bootstrap/Form';
import { useFetch } from '../../../common/hooks/useFetch';
import { getWarehouses } from '../services/WarehouseService';

/**
 * The chosen warehouse id ('' = none): the `?warehouseId=` of the URL until the user picks another one (the router
 * query is only known after hydration, so it is read on every render instead of copied into state once).
 */
export function useWarehouseId(): [string, (id: string) => void] {
  const router = useRouter();
  const [picked, setPicked] = useState<string>();
  const fromQuery = typeof router.query.warehouseId === 'string' ? router.query.warehouseId : '';
  return [picked ?? fromQuery, setPicked];
}

type Props = { value: string; onChange: (id: string) => void };

export default function WarehouseSelect({ value, onChange }: Props) {
  const { data: warehouses } = useFetch(getWarehouses);
  return (
    <Form.Group controlId="warehouse-select" style={{ maxWidth: '24rem' }}>
      <Form.Label>Warehouse</Form.Label>
      <Form.Select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">— select a warehouse —</option>
        {(warehouses ?? []).map((w) => (
          <option key={w.id} value={String(w.id)}>
            {w.name}
          </option>
        ))}
      </Form.Select>
    </Form.Group>
  );
}
