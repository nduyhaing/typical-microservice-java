import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import InputGroup from 'react-bootstrap/InputGroup';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { CONTENT_TYPE_JSON, MIN_SECRET_LENGTH, WebhookDetail, WebhookPost } from '../models/Webhook';
import { getEvents } from '../services/EventService';
import { generateSecret } from '../services/secret';

type Props = {
  /** The webhook being edited; absent when creating. */
  initial?: WebhookDetail;
  /** Saves the webhook; a rejection (e.g. a refused private address) is shown on the form. */
  onSubmit: (webhook: WebhookPost) => Promise<void>;
};

/**
 * Webhook create/edit form (YAS WebhookInformation + EventInformation). The secret is write-only: a new webhook
 * starts with a generated one (copy it to the receiver before saving); when editing, the field is empty (the
 * stored secret is kept) and Regenerate puts a new one in.
 */
export default function WebhookForm({ initial, onSubmit }: Props) {
  const creating = initial === undefined;
  const { data: events, error: eventsError } = useFetch(getEvents);
  const [payloadUrl, setPayloadUrl] = useState(initial?.payloadUrl ?? '');
  const [secret, setSecret] = useState(() => (creating ? generateSecret() : ''));
  const [isActive, setIsActive] = useState(initial?.isActive ?? true);
  const [eventIds, setEventIds] = useState<number[]>(initial?.events.map((event) => event.id) ?? []);
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<unknown>();
  const [saving, setSaving] = useState(false);

  function toggle(id: number, checked: boolean) {
    setEventIds((ids) => (checked ? [...ids.filter((other) => other !== id), id] : ids.filter((other) => other !== id)));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const url = payloadUrl.trim();
    const found: string[] = [];
    if (!url) {
      found.push('Payload URL is required.');
    }
    if ((creating || secret !== '') && secret.length < MIN_SECRET_LENGTH) {
      found.push(`The secret must have at least ${MIN_SECRET_LENGTH} characters.`);
    }
    setProblems(found);
    if (found.length > 0) {
      return;
    }
    setError(undefined);
    setSaving(true);
    try {
      await onSubmit({
        payloadUrl: url,
        secret: secret === '' ? null : secret,
        contentType: CONTENT_TYPE_JSON,
        isActive,
        events: [...eventIds].sort((a, b) => a - b).map((id) => ({ id })),
      });
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Form onSubmit={submit} noValidate style={{ maxWidth: '40rem' }}>
      {problems.length > 0 && (
        <Alert variant="warning">
          {problems.map((problem) => (
            <div key={problem}>{problem}</div>
          ))}
        </Alert>
      )}
      {error !== undefined && <ErrorMessage error={error} />}
      <Form.Group className="mb-3" controlId="webhook-payload-url">
        <Form.Label>Payload URL</Form.Label>
        <Form.Control
          type="url"
          value={payloadUrl}
          required
          maxLength={2048}
          placeholder="https://hooks.example.com/shop"
          onChange={(e) => setPayloadUrl(e.target.value)}
        />
        <Form.Text>Public http(s) addresses only: private, loopback and link-local hosts are refused.</Form.Text>
      </Form.Group>
      <Form.Group className="mb-3" controlId="webhook-content-type">
        <Form.Label>Content type</Form.Label>
        <Form.Control value={CONTENT_TYPE_JSON} disabled readOnly />
      </Form.Group>
      <Form.Group className="mb-3" controlId="webhook-secret">
        <Form.Label>Secret</Form.Label>
        <InputGroup>
          <Form.Control
            value={secret}
            maxLength={255}
            autoComplete="off"
            spellCheck={false}
            placeholder={creating ? '' : 'Leave empty to keep the current secret'}
            onChange={(e) => setSecret(e.target.value)}
          />
          <Button variant="outline-secondary" onClick={() => setSecret(generateSecret())}>
            Regenerate
          </Button>
        </InputGroup>
        <Form.Text>
          {creating
            ? 'Deliveries carry X-Hub-Signature-256: sha256=HMAC of the body with this secret. Copy it now: it is not shown again.'
            : `Current secret: ${initial.maskedSecret}. A new secret is shown only until you save.`}
        </Form.Text>
      </Form.Group>
      <Form.Group className="mb-3">
        <Form.Check type="checkbox" id="webhook-active" label="Active" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />
      </Form.Group>
      <fieldset className="mb-3">
        <legend className="fs-6">Events</legend>
        {eventsError && <ErrorMessage error={eventsError} />}
        {!events && !eventsError && <p>Loading events...</p>}
        {events?.map((event) => (
          <Form.Check
            key={event.id}
            type="checkbox"
            id={`webhook-event-${event.id}`}
            label={event.name}
            checked={eventIds.includes(event.id)}
            onChange={(e) => toggle(event.id, e.target.checked)}
          />
        ))}
      </fieldset>
      <Button type="submit" disabled={saving}>
        Save
      </Button>{' '}
      <Link href="/webhook" className="btn btn-outline-secondary">
        Cancel
      </Link>
    </Form>
  );
}
