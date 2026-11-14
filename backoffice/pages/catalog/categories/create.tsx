import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import CategoryForm from '../../../modules/catalog/components/CategoryForm';
import { createCategory } from '../../../modules/catalog/services/CategoryService';

export default function CategoryCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create category">
      <h1 className="h3 mb-3">Create category</h1>
      <CategoryForm
        onSubmit={async (category) => {
          await createCategory(category);
          await router.push('/catalog/categories');
        }}
      />
    </Layout>
  );
}
