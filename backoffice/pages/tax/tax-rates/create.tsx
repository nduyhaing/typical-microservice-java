import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import TaxRateForm from '../../../modules/tax/components/TaxRateForm';
import { createTaxRate } from '../../../modules/tax/services/TaxRateService';

export default function TaxRateCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create tax rate">
      <h1 className="h3 mb-3">Create tax rate</h1>
      <TaxRateForm
        onSubmit={async (rate) => {
          await createTaxRate(rate);
          await router.push('/tax/tax-rates');
        }}
      />
    </Layout>
  );
}
