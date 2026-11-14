import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import BrandForm from '../../../../modules/catalog/components/BrandForm';
import { Brand } from '../../../../modules/catalog/models/Brand';
import { getBrand, updateBrand } from '../../../../modules/catalog/services/BrandService';

export default function BrandEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: brand, error } = useFetch(() => (id ? getBrand(id) : pending<Brand>()), [id]);

  return (
    <Layout title="Edit brand">
      <h1 className="h3 mb-3">Edit brand{brand ? `: ${brand.name}` : ''}</h1>
      {error && <ErrorMessage error={error} />}
      {!brand && !error && <p>Loading...</p>}
      {brand && id && (
        <BrandForm
          initial={{ name: brand.name, slug: brand.slug, isPublish: Boolean(brand.isPublish) }}
          onSubmit={async (body) => {
            await updateBrand(id, body);
            await router.push('/catalog/brands');
          }}
        />
      )}
    </Layout>
  );
}
