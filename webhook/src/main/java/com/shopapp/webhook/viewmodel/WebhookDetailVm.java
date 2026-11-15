package com.shopapp.webhook.viewmodel;

import java.util.List;

/**
 * YAS WebhookDetailVm without {@code secret} (YAS left it null): {@code maskedSecret} shows only the last four
 * characters (decisions.md 5-31).
 */
public record WebhookDetailVm(Long id, String payloadUrl, String contentType, Boolean isActive, String maskedSecret,
                              List<EventVm> events) {
}
