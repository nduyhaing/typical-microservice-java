import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import ProductForm, { ProductFormValues, toFormValues } from '../../../../modules/catalog/components/ProductForm';
import { getProductOptions } from '../../../../modules/catalog/services/ProductOptionService';
import {
  getProduct,
  getProductVariations,
  getRelatedProductIds,
  updateProduct,
} from '../../../../modules/catalog/services/ProductService';

/** The product with its variations, related products and the option names, as form values. */
async function loadProduct(id: number): Promise<ProductFormValues> {
  const [product, variations, related, options] = await Promise.all([
    getProduct(id),
    getProductVariations(id),
    getRelatedProductIds(id),
    getProductOptions(),
  ]);
  return toFormValues(product, variations, related, options);
}

export default function ProductEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: values, error } = useFetch(() => (id ? loadProduct(id) : pending<ProductFormValues>()), [id]);

  return (
    <Layout title="Edit product">
      <h1 className="h3 mb-3">Edit product{values ? `: ${values.name}` : ''}</h1>
      {error && <ErrorMessage error={error} />}
      {!values && !error && <p>Loading...</p>}
      {values && id && (
        <ProductForm
          initial={values}
          isNew={false}
          onSubmit={async (payload) => {
            await updateProduct(id, payload);
            await router.push('/catalog/products');
          }}
        />
      )}
    </Layout>
  );
}
