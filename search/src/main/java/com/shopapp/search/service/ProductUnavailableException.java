package com.shopapp.search.service;

/** Product did not answer usably (down, timeout, 5xx, refused): the event is retried, then dead-lettered. */
public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
