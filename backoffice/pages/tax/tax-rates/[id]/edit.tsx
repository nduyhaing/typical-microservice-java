import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import TaxRateForm from '../../../../modules/tax/components/TaxRateForm';
import { TaxRate } from '../../../../modules/tax/models/Tax';
import { getTaxRate, updateTaxRate } from '../../../../modules/tax/services/TaxRateService';

export default function TaxRateEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: rate, error } = useFetch(() => (id ? getTaxRate(id) : pending<TaxRate>()), [id]);

  return (
    <Layout title="Edit tax rate">
      <h1 className="h3 mb-3">Edit tax rate</h1>
      {error && <ErrorMessage error={error} />}
      {!rate && !error && <p>Loading...</p>}
      {rate && id && (
        <TaxRateForm
          initial={{
            rate: rate.rate,
            zipCode: rate.zipCode ?? '',
            taxClassId: rate.taxClassId,
            stateOrProvinceId: rate.stateOrProvinceId,
            countryId: rate.countryId,
          }}
          onSubmit={async (body) => {
            await updateTaxRate(id, body);
            await router.push('/tax/tax-rates');
          }}
        />
      )}
    </Layout>
  );
}
