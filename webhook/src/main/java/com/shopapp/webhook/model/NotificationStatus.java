package com.shopapp.webhook.model;

/** A delivery's state (YAS: NOTIFYING / NOTIFIED only), decisions.md 5-34. */
public enum NotificationStatus {
    /** Waiting for its first attempt. */
    PENDING,
    /** Claimed by the delivery worker, attempt in flight. */
    SENDING,
    /** Failed, another attempt is due at next_attempt_on. */
    RETRYING,
    /** The receiver answered 2xx. */
    DELIVERED,
    /** Out of attempts, or a failure that is not retried (refused address, 4xx, oversized payload). */
    FAILED,
    /** The webhook was deactivated before the delivery was sent. */
    CANCELLED
}
