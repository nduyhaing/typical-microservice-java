import { useRouter } from 'next/router';
import ErrorMessage from '../../../../common/components/ErrorMessage';
import Layout from '../../../../common/components/Layout';
import { useFetch } from '../../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../../common/hooks/useRecordId';
import StateOrProvinceForm from '../../../../modules/location/components/StateOrProvinceForm';
import { StateOrProvince } from '../../../../modules/location/models/Location';
import {
  getStateOrProvince,
  updateStateOrProvince,
} from '../../../../modules/location/services/StateOrProvinceService';

export default function StateOrProvinceEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: state, error } = useFetch(() => (id ? getStateOrProvince(id) : pending<StateOrProvince>()), [id]);

  return (
    <Layout title="Edit state or province">
      <h1 className="h3 mb-3">Edit state or province{state ? `: ${state.name}` : ''}</h1>
      {error && <ErrorMessage error={error} />}
      {!state && !error && <p>Loading...</p>}
      {state && id && (
        <StateOrProvinceForm
          initial={{ name: state.name, code: state.code, type: state.type, countryId: state.countryId }}
          onSubmit={async (body) => {
            await updateStateOrProvince(id, body);
            await router.push('/location/state-or-provinces');
          }}
        />
      )}
    </Layout>
  );
}
