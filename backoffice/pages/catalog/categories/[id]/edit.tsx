import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import CategoryForm from '../../../../modules/catalog/components/CategoryForm';
import { CategoryDetail } from '../../../../modules/catalog/models/Category';
import { getCategory, updateCategory } from '../../../../modules/catalog/services/CategoryService';

export default function CategoryEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: category, error } = useFetch(() => (id ? getCategory(id) : pending<CategoryDetail>()), [id]);

  return (
    <Layout title="Edit category">
      <h1 className="h3 mb-3">Edit category{category ? `: ${category.name}` : ''}</h1>
      {error && <ErrorMessage error={error} />}
      {!category && !error && <p>Loading...</p>}
      {category && id && (
        <CategoryForm
          category={category}
          onSubmit={async (body) => {
            await updateCategory(id, body);
            await router.push('/catalog/categories');
          }}
        />
      )}
    </Layout>
  );
}
