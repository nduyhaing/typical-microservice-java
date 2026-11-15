import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import StateOrProvinceForm from '../../../modules/location/components/StateOrProvinceForm';
import { createStateOrProvince } from '../../../modules/location/services/StateOrProvinceService';

export default function StateOrProvinceCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create state or province">
      <h1 className="h3 mb-3">Create state or province</h1>
      <StateOrProvinceForm
        onSubmit={async (state) => {
          await createStateOrProvince(state);
          await router.push('/location/state-or-provinces');
        }}
      />
    </Layout>
  );
}
