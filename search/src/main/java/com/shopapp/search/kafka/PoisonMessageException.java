package com.shopapp.search.kafka;

/** A message that can never be processed: sent to the dead-letter topic at once, without retries. */
public class PoisonMessageException extends RuntimeException {

    public PoisonMessageException(String message) {
        super(message);
    }
}
