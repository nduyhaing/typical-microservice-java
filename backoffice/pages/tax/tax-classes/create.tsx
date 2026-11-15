import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import TaxClassForm from '../../../modules/tax/components/TaxClassForm';
import { createTaxClass } from '../../../modules/tax/services/TaxClassService';

export default function TaxClassCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create tax class">
      <h1 className="h3 mb-3">Create tax class</h1>
      <TaxClassForm
        onSubmit={async (taxClass) => {
          await createTaxClass(taxClass);
          await router.push('/tax/tax-classes');
        }}
      />
    </Layout>
  );
}
