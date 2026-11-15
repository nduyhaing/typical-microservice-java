import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import PromotionForm from '../../../modules/promotion/components/PromotionForm';
import { createPromotion } from '../../../modules/promotion/services/PromotionService';

export default function PromotionCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create promotion">
      <h1 className="h3 mb-3">Create promotion</h1>
      <PromotionForm
        onSubmit={async (promotion) => {
          await createPromotion(promotion);
          await router.push('/promotion/manager-promotion');
        }}
      />
    </Layout>
  );
}
