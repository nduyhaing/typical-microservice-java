import { useRouter } from 'next/router';
import Layout from '../../common/components/Layout';
import WebhookForm from '../../modules/webhook/components/WebhookForm';
import { createWebhook } from '../../modules/webhook/services/WebhookService';

export default function WebhookCreatePage() {
  const router = useRouter();
  return (
    <Layout title="Create webhook">
      <h1 className="h3 mb-3">Create webhook</h1>
      <WebhookForm
        onSubmit={async (webhook) => {
          await createWebhook(webhook);
          await router.push('/webhook');
        }}
      />
    </Layout>
  );
}
