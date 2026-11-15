package com.shopapp.webhook.delivery;

/**
 * The outcome of one delivery attempt.
 *
 * @param outcome    delivered, worth retrying, or not
 * @param statusCode the receiver's HTTP status; null when no response arrived
 * @param error      what went wrong (null when delivered), for the delivery log
 */
public record SendResult(Outcome outcome, Integer statusCode, String error) {

    /** What the attempt means for the delivery. */
    public enum Outcome {
        /** 2xx. */
        DELIVERED,
        /** 5xx, 408, 429, timeouts, connection failures: tried again later. */
        RETRYABLE_FAILURE,
        /** Other statuses (incl. redirects, never followed), refused addresses, invalid URL, oversized payload. */
        PERMANENT_FAILURE
    }

    static SendResult delivered(int statusCode) {
        return new SendResult(Outcome.DELIVERED, statusCode, null);
    }

    static SendResult retryable(Integer statusCode, String error) {
        return new SendResult(Outcome.RETRYABLE_FAILURE, statusCode, error);
    }

    static SendResult permanent(Integer statusCode, String error) {
        return new SendResult(Outcome.PERMANENT_FAILURE, statusCode, error);
    }
}
