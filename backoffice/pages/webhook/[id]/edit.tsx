import { useRouter } from 'next/router';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useFetch } from '../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../common/hooks/useRecordId';
import WebhookForm from '../../../modules/webhook/components/WebhookForm';
import { WebhookDetail } from '../../../modules/webhook/models/Webhook';
import { getWebhook, updateWebhook } from '../../../modules/webhook/services/WebhookService';

export default function WebhookEditPage() {
  const router = useRouter();
  const id = useRecordId();
  const { data: webhook, error } = useFetch(() => (id ? getWebhook(id) : pending<WebhookDetail>()), [id]);

  return (
    <Layout title="Edit webhook">
      <h1 className="h3 mb-3">Edit webhook{webhook ? ` ${webhook.id}` : ''}</h1>
      {error && <ErrorMessage error={error} />}
      {!webhook && !error && <p>Loading...</p>}
      {webhook && id && (
        <WebhookForm
          initial={webhook}
          onSubmit={async (body) => {
            await updateWebhook(id, body);
            await router.push('/webhook');
          }}
        />
      )}
    </Layout>
  );
}
