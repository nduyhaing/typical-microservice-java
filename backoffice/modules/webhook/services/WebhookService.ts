import { PAGE_SIZE } from '../../../common/models/Page';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { WebhookDetail, WebhookNotificationPage, WebhookPage, WebhookPost } from '../models/Webhook';

const base = '/webhook/backoffice/webhooks';

export const getWebhookPage = (pageNo: number) => get<WebhookPage>(`${base}/paging?pageNo=${pageNo}&pageSize=${PAGE_SIZE}`);

export const getWebhook = (id: number) => get<WebhookDetail>(`${base}/${id}`);

export const createWebhook = (webhook: WebhookPost) => post<WebhookDetail>(base, webhook);

export const updateWebhook = (id: number, webhook: WebhookPost) => put<null>(`${base}/${id}`, webhook);

export const deleteWebhook = (id: number) => del(`${base}/${id}`);

/** The webhook's delivery log, newest first. */
export const getNotificationPage = (id: number, pageNo: number) =>
  get<WebhookNotificationPage>(`${base}/${id}/notifications?pageNo=${pageNo}&pageSize=${PAGE_SIZE}`);

/** Queues a PING delivery; the log shows how it went. */
export const sendTestDelivery = (id: number) => post<{ deliveryId: string }>(`${base}/${id}/test`, {});
