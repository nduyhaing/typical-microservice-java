package com.shopapp.webhook.model;

/** The events a webhook can subscribe to (YAS EventName; rows of the event table). */
public enum EventName {
    ON_PRODUCT_UPDATED,
    ON_ORDER_CREATED,
    ON_ORDER_STATUS_UPDATED
}
