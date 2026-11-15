import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { NAV_GROUPS } from '../common/components/Layout';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as eventService from '../modules/webhook/services/EventService';
import * as webhookService from '../modules/webhook/services/WebhookService';
import { generateSecret } from '../modules/webhook/services/secret';
import WebhookEditPage from '../pages/webhook/[id]/edit';
import WebhookNotificationsPage from '../pages/webhook/[id]/notifications';
import WebhookCreatePage from '../pages/webhook/create';
import WebhooksPage from '../pages/webhook/index';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/webhook/services/EventService');
vi.mock('../modules/webhook/services/WebhookService');

const events = [
  { id: 1, name: 'ON_PRODUCT_UPDATED' },
  { id: 2, name: 'ON_ORDER_CREATED' },
  { id: 3, name: 'ON_ORDER_STATUS_UPDATED' },
];
const page = <T,>(key: string, content: T[]) => ({ [key]: content, pageNo: 0, pageSize: 10, totalElements: content.length, totalPages: 1, isLast: true });

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
  vi.mocked(eventService.getEvents).mockResolvedValue(events);
});
afterEach(() => {
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

describe('Webhooks', () => {
  it('is in the navigation', () => {
    const links = NAV_GROUPS.flatMap((group) => group.links);
    expect(links).toContainEqual(expect.objectContaining({ href: '/webhook', label: 'Webhooks' }));
  });

  it('lists webhooks with their status and deletes after confirming', async () => {
    vi.mocked(webhookService.getWebhookPage).mockResolvedValue(
      page('webhooks', [
        { id: 2, payloadUrl: 'https://hooks.example.com/b', contentType: 'application/json', isActive: false },
        { id: 1, payloadUrl: 'https://hooks.example.com/a', contentType: 'application/json', isActive: true },
      ]) as never
    );
    vi.mocked(webhookService.deleteWebhook).mockResolvedValue(null);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<WebhooksPage />);

    const active = (await screen.findByText('https://hooks.example.com/a')).closest('tr')!;
    expect(within(active).getByText('Active')).toBeInTheDocument();
    expect(within(active).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/webhook/1/edit');
    expect(within(active).getByRole('link', { name: 'Deliveries' })).toHaveAttribute('href', '/webhook/1/notifications');
    const inactive = screen.getByText('https://hooks.example.com/b').closest('tr')!;
    expect(within(inactive).getByText('Inactive')).toBeInTheDocument();

    await userEvent.click(within(active).getByRole('button', { name: 'Delete' }));
    expect(window.confirm).toHaveBeenCalledWith('Delete webhook "https://hooks.example.com/a"?');
    expect(webhookService.deleteWebhook).toHaveBeenCalledWith(1);
  });

  it('creates a webhook with a generated secret and the chosen events', async () => {
    vi.mocked(webhookService.createWebhook).mockResolvedValue({} as never);

    render(<WebhookCreatePage />);
    const secret = screen.getByLabelText('Secret') as HTMLInputElement;
    expect(secret.value).toMatch(/^[0-9a-f]{64}$/);
    const first = secret.value;
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate' }));
    expect(secret.value).toMatch(/^[0-9a-f]{64}$/);
    expect(secret.value).not.toBe(first);

    await userEvent.type(screen.getByLabelText('Payload URL'), ' https://hooks.example.com/shop ');
    expect(screen.getByLabelText('Content type')).toBeDisabled();
    await userEvent.click(await screen.findByLabelText('ON_ORDER_CREATED'));
    await userEvent.click(screen.getByLabelText('ON_PRODUCT_UPDATED'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(webhookService.createWebhook).toHaveBeenCalledWith({
      payloadUrl: 'https://hooks.example.com/shop',
      secret: secret.value,
      contentType: 'application/json',
      isActive: true,
      events: [{ id: 1 }, { id: 2 }],
    });
    expect(push).toHaveBeenCalledWith('/webhook');
  });

  it("shows the service's refusal of a private address", async () => {
    vi.mocked(webhookService.createWebhook).mockRejectedValue(
      new ApiError(400, { detail: 'Payload URL host 169.254.169.254 resolves to a loopback, private, link-local or other non-public address', fieldErrors: [] })
    );

    render(<WebhookCreatePage />);
    await userEvent.type(screen.getByLabelText('Payload URL'), 'http://169.254.169.254/');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('non-public address');
    expect(push).not.toHaveBeenCalled();
  });

  it('requires a payload URL and a secret of at least 16 characters', async () => {
    render(<WebhookCreatePage />);
    await userEvent.clear(screen.getByLabelText('Secret'));
    await userEvent.type(screen.getByLabelText('Secret'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('Payload URL is required.')).toBeInTheDocument();
    expect(screen.getByText('The secret must have at least 16 characters.')).toBeInTheDocument();
    expect(webhookService.createWebhook).not.toHaveBeenCalled();
  });

  it('edits a webhook: the secret is write-only, empty keeps it, Regenerate replaces it', async () => {
    router.query = { id: '4' };
    vi.mocked(webhookService.getWebhook).mockResolvedValue({
      id: 4,
      payloadUrl: 'https://hooks.example.com/a',
      contentType: 'application/json',
      isActive: true,
      maskedSecret: '********cdef',
      events: [events[2]],
    });
    vi.mocked(webhookService.updateWebhook).mockResolvedValue(null);

    render(<WebhookEditPage />);
    const secret = (await screen.findByLabelText('Secret')) as HTMLInputElement;
    expect(secret.value).toBe('');
    expect(screen.getByText(/Current secret: \*{8}cdef/)).toBeInTheDocument();
    expect(await screen.findByLabelText('ON_ORDER_STATUS_UPDATED')).toBeChecked();
    await userEvent.click(screen.getByLabelText('Active'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(webhookService.updateWebhook).toHaveBeenLastCalledWith(4, {
      payloadUrl: 'https://hooks.example.com/a',
      secret: null,
      contentType: 'application/json',
      isActive: false,
      events: [{ id: 3 }],
    });

    await userEvent.click(screen.getByRole('button', { name: 'Regenerate' }));
    const regenerated = secret.value;
    expect(regenerated).toMatch(/^[0-9a-f]{64}$/);
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(vi.mocked(webhookService.updateWebhook).mock.lastCall?.[1].secret).toBe(regenerated);
  });

  it('shows the delivery log and queues a test event', async () => {
    router.query = { id: '4' };
    vi.mocked(webhookService.getWebhook).mockResolvedValue({
      id: 4,
      payloadUrl: 'https://hooks.example.com/a',
      contentType: 'application/json',
      isActive: true,
      maskedSecret: '********cdef',
      events: [],
    });
    vi.mocked(webhookService.getNotificationPage).mockResolvedValue(
      page('notifications', [
        {
          id: 8,
          deliveryId: 'd-8',
          eventName: 'ON_ORDER_CREATED',
          status: 'RETRYING',
          attempts: 1,
          responseCode: 500,
          lastError: 'HTTP 500',
          payloadSha256: 'abc',
          createdOn: '2026-11-15T10:00:00Z',
          lastAttemptOn: '2026-11-15T10:00:01Z',
          nextAttemptOn: '2026-11-15T10:00:11Z',
          deliveredOn: null,
        },
      ]) as never
    );
    vi.mocked(webhookService.sendTestDelivery).mockResolvedValue({ deliveryId: 'd-9' });

    render(<WebhookNotificationsPage />);

    const row = (await screen.findByText('ON_ORDER_CREATED')).closest('tr')!;
    expect(within(row).getByText('RETRYING')).toBeInTheDocument();
    expect(within(row).getByText('500')).toBeInTheDocument();
    expect(within(row).getByText('HTTP 500')).toBeInTheDocument();
    expect(screen.getByText('https://hooks.example.com/a')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Send test event' }));
    expect(webhookService.sendTestDelivery).toHaveBeenCalledWith(4);
    expect(await screen.findByText(/Test delivery d-9 queued/)).toBeInTheDocument();
  });

  it('generates 32 random bytes as hex', () => {
    const a = generateSecret();
    expect(a).toMatch(/^[0-9a-f]{64}$/);
    expect(generateSecret()).not.toBe(a);
  });
});
