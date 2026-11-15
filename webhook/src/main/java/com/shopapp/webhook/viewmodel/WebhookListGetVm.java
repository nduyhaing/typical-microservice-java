package com.shopapp.webhook.viewmodel;

import java.util.List;

/** YAS WebhookListGetVm. */
public record WebhookListGetVm(List<WebhookVm> webhooks, int pageNo, int pageSize, long totalElements,
                               int totalPages, boolean isLast) {
}
