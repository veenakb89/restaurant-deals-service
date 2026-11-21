package com.eatclub.deals.exception;

/** The upstream restaurant feed could not be fetched or read (unreachable, timed out, error response or bad JSON). */
public class FeedUnavailableException extends RuntimeException {

    public FeedUnavailableException(Throwable cause) {
        super("Failed to fetch the restaurant feed", cause);
    }
}
