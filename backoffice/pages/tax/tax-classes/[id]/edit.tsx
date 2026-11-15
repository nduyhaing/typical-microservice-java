import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import TaxClassForm from '../../../../modules/tax/components/TaxClassForm';
import { TaxClass } from '../../../../modules/tax/models/Tax';
import { getTaxClass, updateTaxClass } from '../../../../modules/tax/services/TaxClassService';

export default function TaxClassEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: taxClass, error } = useFetch(() => (id ? getTaxClass(id) : pending<TaxClass>()), [id]);

  return (
    <Layout title="Edit tax class">
      <h1 className="h3 mb-3">Edit tax class{taxClass ? `: ${taxClass.name}` : ''}</h1>
      {error && <ErrorMessage error={error} />}
      {!taxClass && !error && <p>Loading...</p>}
      {taxClass && id && (
        <TaxClassForm
          initial={{ name: taxClass.name }}
          onSubmit={async (body) => {
            await updateTaxClass(id, body);
            await router.push('/tax/tax-classes');
          }}
        />
      )}
    </Layout>
  );
}
