import Link from 'next/link';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../common/components/ErrorMessage';
import Layout from '../../common/components/Layout';
import Pager from '../../common/components/Pager';
import { useDeleteAction } from '../../common/hooks/useDeleteAction';
import { useFetch } from '../../common/hooks/useFetch';
import { deleteWebhook, getWebhookPage } from '../../modules/webhook/services/WebhookService';

/** Webhook list (YAS pages/webhook). */
export default function WebhooksPage() {
  const [pageNo, setPageNo] = useState(0);
  const [version, setVersion] = useState(0);
  const { data, error, loading } = useFetch(() => getWebhookPage(pageNo), [pageNo, version]);
  const deletion = useDeleteAction('webhook', () => setVersion((v) => v + 1));

  return (
    <Layout title="Webhooks">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Webhooks</h1>
        <Link href="/webhook/create" className="btn btn-primary">
          Create webhook
        </Link>
      </div>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.webhooks.length === 0 && <p>No webhooks yet.</p>}
      {data && data.webhooks.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Payload URL</th>
              <th>Content type</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.webhooks.map((webhook) => (
              <tr key={webhook.id}>
                <td>{webhook.id}</td>
                <td className="text-break">{webhook.payloadUrl}</td>
                <td>{webhook.contentType}</td>
                <td>{webhook.isActive ? <Badge bg="success">Active</Badge> : <Badge bg="secondary">Inactive</Badge>}</td>
                <td className="text-end text-nowrap">
                  <Link href={`/webhook/${webhook.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Link href={`/webhook/${webhook.id}/notifications`} className="btn btn-sm btn-outline-secondary">
                    Deliveries
                  </Link>{' '}
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(webhook.payloadUrl, () => deleteWebhook(webhook.id))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="webhooks" onChange={setPageNo} />}
    </Layout>
  );
}
