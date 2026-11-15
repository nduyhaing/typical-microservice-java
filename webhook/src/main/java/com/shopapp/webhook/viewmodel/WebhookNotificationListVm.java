package com.shopapp.webhook.viewmodel;

import java.util.List;

/** A page of a webhook's delivery log, newest first. */
public record WebhookNotificationListVm(List<WebhookNotificationVm> notifications, int pageNo, int pageSize,
                                        long totalElements, int totalPages, boolean isLast) {
}
