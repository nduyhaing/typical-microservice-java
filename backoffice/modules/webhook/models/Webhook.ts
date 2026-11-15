import { PageInfo } from '../../../common/models/Page';

/** EventVm. */
export type WebhookEvent = { id: number; name: string };

/** WebhookVm: a list row. */
export type Webhook = { id: number; payloadUrl: string; contentType: string; isActive: boolean };

/** WebhookDetailVm: the secret itself is never returned, only its last four characters (5-31). */
export type WebhookDetail = Webhook & { maskedSecret: string; events: WebhookEvent[] };

/** WebhookPostVm: `secret` null (or empty) on update keeps the stored one. */
export type WebhookPost = {
  payloadUrl: string;
  secret: string | null;
  contentType: string;
  isActive: boolean;
  events: { id: number }[];
};

/** WebhookListGetVm. */
export type WebhookPage = PageInfo & { webhooks: Webhook[] };

export type NotificationStatus = 'PENDING' | 'SENDING' | 'RETRYING' | 'DELIVERED' | 'FAILED' | 'CANCELLED';

/** WebhookNotificationVm: one delivery and its attempts. */
export type WebhookNotification = {
  id: number;
  deliveryId: string;
  eventName: string;
  status: NotificationStatus;
  attempts: number;
  responseCode: number | null;
  lastError: string | null;
  payloadSha256: string;
  createdOn: string;
  lastAttemptOn: string | null;
  nextAttemptOn: string | null;
  deliveredOn: string | null;
};

/** WebhookNotificationListVm. */
export type WebhookNotificationPage = PageInfo & { notifications: WebhookNotification[] };

/** The only content type deliveries are sent with (YAS ContentType). */
export const CONTENT_TYPE_JSON = 'application/json';

/** Minimum secret length the service accepts. */
export const MIN_SECRET_LENGTH = 16;
