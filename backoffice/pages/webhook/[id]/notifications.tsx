import Link from 'next/link';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import Pager from '../../../common/components/Pager';
import { useFetch } from '../../../common/hooks/useFetch';
import { pending, useRecordId } from '../../../common/hooks/useRecordId';
import { NotificationStatus, WebhookDetail, WebhookNotificationPage } from '../../../modules/webhook/models/Webhook';
import { getNotificationPage, getWebhook, sendTestDelivery } from '../../../modules/webhook/services/WebhookService';

const STATUS_VARIANT: Record<NotificationStatus, string> = {
  PENDING: 'secondary',
  SENDING: 'info',
  RETRYING: 'warning',
  DELIVERED: 'success',
  FAILED: 'danger',
  CANCELLED: 'secondary',
};

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString() : '';
}

/** A webhook's delivery log (newest first) and "Send test event". */
export default function WebhookNotificationsPage() {
  const id = useRecordId();
  const [pageNo, setPageNo] = useState(0);
  const [version, setVersion] = useState(0);
  const { data: webhook } = useFetch(() => (id ? getWebhook(id) : pending<WebhookDetail>()), [id]);
  const { data, error, loading } = useFetch(
    () => (id ? getNotificationPage(id, pageNo) : pending<WebhookNotificationPage>()),
    [id, pageNo, version]
  );
  const [notice, setNotice] = useState<string>();
  const [testError, setTestError] = useState<unknown>();

  async function sendTest() {
    if (!id) {
      return;
    }
    setNotice(undefined);
    setTestError(undefined);
    try {
      const { deliveryId } = await sendTestDelivery(id);
      setNotice(`Test delivery ${deliveryId} queued.`);
      setVersion((v) => v + 1);
    } catch (e) {
      setTestError(e);
    }
  }

  return (
    <Layout title="Webhook deliveries">
      <div className="d-flex justify-content-between align-items-center mb-3 gap-2 flex-wrap">
        <div>
          <h1 className="h3 m-0">Deliveries{id ? ` of webhook ${id}` : ''}</h1>
          {webhook && <div className="text-muted text-break">{webhook.payloadUrl}</div>}
        </div>
        <div className="d-flex gap-2">
          <Button variant="outline-secondary" onClick={() => setVersion((v) => v + 1)}>
            Refresh
          </Button>
          <Button onClick={sendTest}>Send test event</Button>
          <Link href="/webhook" className="btn btn-outline-secondary">
            Back
          </Link>
        </div>
      </div>
      {notice && <Alert variant="success">{notice}</Alert>}
      {testError !== undefined && <ErrorMessage error={testError} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.notifications.length === 0 && <p>No deliveries yet.</p>}
      {data && data.notifications.length > 0 && (
        <Table striped hover responsive size="sm">
          <thead>
            <tr>
              <th>Created</th>
              <th>Event</th>
              <th>Status</th>
              <th>Attempts</th>
              <th>Response</th>
              <th>Last error</th>
              <th>Last attempt</th>
              <th>Next attempt</th>
            </tr>
          </thead>
          <tbody>
            {data.notifications.map((notification) => (
              <tr key={notification.id} title={`Delivery ${notification.deliveryId}`}>
                <td className="text-nowrap">{formatTime(notification.createdOn)}</td>
                <td>{notification.eventName}</td>
                <td>
                  <Badge bg={STATUS_VARIANT[notification.status] ?? 'secondary'}>{notification.status}</Badge>
                </td>
                <td>{notification.attempts}</td>
                <td>{notification.responseCode ?? ''}</td>
                <td className="text-break">{notification.lastError ?? ''}</td>
                <td className="text-nowrap">{formatTime(notification.lastAttemptOn)}</td>
                <td className="text-nowrap">{formatTime(notification.nextAttemptOn)}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="deliveries" onChange={setPageNo} />}
    </Layout>
  );
}
