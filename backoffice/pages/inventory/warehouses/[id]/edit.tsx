import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import WarehouseForm from '../../../../modules/inventory/components/WarehouseForm';
import { WarehouseDetail } from '../../../../modules/inventory/models/Inventory';
import { getWarehouse, updateWarehouse } from '../../../../modules/inventory/services/WarehouseService';

export default function WarehouseEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: warehouse, error } = useFetch(() => (id ? getWarehouse(id) : pending<WarehouseDetail>()), [id]);

  return (
    <Layout title="Edit warehouse">
      <h1 className="h3 mb-3">Edit warehouse{warehouse ? `: ${warehouse.name}` : ''}</h1>
      {error && <ErrorMessage error={error} />}
      {!warehouse && !error && <p>Loading...</p>}
      {warehouse && id && (
        <WarehouseForm
          initial={warehouse}
          onSubmit={async (body) => {
            await updateWarehouse(id, body);
            await router.push('/inventory/warehouses');
          }}
        />
      )}
    </Layout>
  );
}
