import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import ProductForm, { NEW_PRODUCT } from '../../../modules/catalog/components/ProductForm';
import { createProduct } from '../../../modules/catalog/services/ProductService';

export default function ProductCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create product">
      <h1 className="h3 mb-3">Create product</h1>
      <ProductForm
        initial={NEW_PRODUCT}
        isNew
        onSubmit={async (product) => {
          await createProduct(product);
          await router.push('/catalog/products');
        }}
      />
    </Layout>
  );
}
