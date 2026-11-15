package com.shopapp.webhook.viewmodel;

import java.util.UUID;

/** The queued test delivery ("send test event"). */
public record TestDeliveryVm(UUID deliveryId) {
}
