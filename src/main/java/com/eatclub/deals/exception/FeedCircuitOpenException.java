package com.eatclub.deals.exception;

/**
 * The feed wasn't called at all because its circuit breaker is open (it has been failing recently).
 * Clients see the same error as any other feed failure; only the logging differs, as this is expected during an outage.
 */
public class FeedCircuitOpenException extends FeedUnavailableException {

    public FeedCircuitOpenException(Throwable cause) {
        super(cause);
    }
}
