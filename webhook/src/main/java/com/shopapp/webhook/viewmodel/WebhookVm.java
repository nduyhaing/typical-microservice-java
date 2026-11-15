package com.shopapp.webhook.viewmodel;

import com.shopapp.webhook.model.Webhook;

/** YAS WebhookVm: a list row. */
public record WebhookVm(Long id, String payloadUrl, String contentType, Boolean isActive) {

    public static WebhookVm fromModel(Webhook webhook) {
        return new WebhookVm(webhook.getId(), webhook.getPayloadUrl(), webhook.getContentType(),
            webhook.getIsActive());
    }
}
