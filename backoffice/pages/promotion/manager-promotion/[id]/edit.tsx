import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import PromotionForm from '../../../../modules/promotion/components/PromotionForm';
import { PromotionDetail } from '../../../../modules/promotion/models/Promotion';
import { getPromotion, updatePromotion } from '../../../../modules/promotion/services/PromotionService';

export default function PromotionEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: promotion, error } = useFetch(() => (id ? getPromotion(id) : pending<PromotionDetail>()), [id]);

  return (
    <Layout title="Edit promotion">
      <h1 className="h3 mb-3">Edit promotion</h1>
      {error && <ErrorMessage error={error} />}
      {!promotion && !error && <p>Loading...</p>}
      {promotion && id && (
        <PromotionForm
          initial={promotion}
          onSubmit={async (body) => {
            await updatePromotion(id, body);
            await router.push('/promotion/manager-promotion');
          }}
        />
      )}
    </Layout>
  );
}
