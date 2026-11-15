import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import CountryForm, { toCountryPost } from '../../../../modules/location/components/CountryForm';
import { Country } from '../../../../modules/location/models/Location';
import { getCountry, updateCountry } from '../../../../modules/location/services/CountryService';

export default function CountryEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: country, error } = useFetch(() => (id ? getCountry(id) : pending<Country>()), [id]);

  return (
    <Layout title="Edit country">
      <h1 className="h3 mb-3">Edit country{country ? `: ${country.name}` : ''}</h1>
      {error && <ErrorMessage error={error} />}
      {!country && !error && <p>Loading...</p>}
      {country && id && (
        <CountryForm
          initial={toCountryPost(country)}
          onSubmit={async (body) => {
            await updateCountry(id, body);
            await router.push('/location/countries');
          }}
        />
      )}
    </Layout>
  );
}
