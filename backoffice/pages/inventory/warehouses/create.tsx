import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import WarehouseForm from '../../../modules/inventory/components/WarehouseForm';
import { createWarehouse } from '../../../modules/inventory/services/WarehouseService';

export default function WarehouseCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create warehouse">
      <h1 className="h3 mb-3">Create warehouse</h1>
      <WarehouseForm
        onSubmit={async (warehouse) => {
          await createWarehouse(warehouse);
          await router.push('/inventory/warehouses');
        }}
      />
    </Layout>
  );
}
