import { get } from '../../../common/services/ApiClientService';
import { WebhookEvent } from '../models/Webhook';

/** The subscribable events. */
export const getEvents = () => get<WebhookEvent[]>('/webhook/backoffice/events');
