import { useRouter } from 'next/router';
import Layout from '../../../common/components/Layout';
import CountryForm from '../../../modules/location/components/CountryForm';
import { createCountry } from '../../../modules/location/services/CountryService';

export default function CountryCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create country">
      <h1 className="h3 mb-3">Create country</h1>
      <CountryForm
        onSubmit={async (country) => {
          await createCountry(country);
          await router.push('/location/countries');
        }}
      />
    </Layout>
  );
}
