import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import BrandForm from '../../../modules/catalog/components/BrandForm';
import { createBrand } from '../../../modules/catalog/services/BrandService';

export default function BrandCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create brand">
      <h1 className="h3 mb-3">Create brand</h1>
      <BrandForm
        onSubmit={async (brand) => {
          await createBrand(brand);
          await router.push('/catalog/brands');
        }}
      />
    </Layout>
  );
}
