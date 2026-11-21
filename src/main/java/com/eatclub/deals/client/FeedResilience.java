package com.eatclub.deals.client;

import com.eatclub.deals.client.RestaurantFeedProperties.CircuitBreakerSettings;
import com.eatclub.deals.client.RestaurantFeedProperties.RetrySettings;
import com.eatclub.deals.exception.FeedUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

/**
 * The resilience policy for calls to the upstream feed.
 * <ul>
 *   <li><b>Retry:</b> a transient failure (connection error, timeout or upstream 5xx) is retried with exponential
 *       backoff. Upstream 4xx and invalid JSON aren't retried; retrying won't fix them.</li>
 *   <li><b>Circuit breaker:</b> when too many recent calls fail, it opens and calls fail fast without reaching
 *       upstream. After a while it lets a few trial calls through and closes again if they succeed.</li>
 * </ul>
 * Retry wraps the circuit breaker, so every attempt is counted by the breaker, and a call rejected because the
 * breaker is open is never retried. Every failure is a {@link FeedUnavailableException}.
 */
@Component
public class FeedResilience {

    private static final Logger log = LoggerFactory.getLogger(FeedResilience.class);
    private static final String NAME = "restaurantFeed";

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public FeedResilience(RestaurantFeedProperties properties) {
        this.retry = retry(properties.retry());
        this.circuitBreaker = circuitBreaker(properties.circuitBreaker());
    }

    /** Runs {@code feedCall} with retry and the circuit breaker. */
    public <T> T call(Supplier<T> feedCall) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, feedCall);
        return Retry.decorateSupplier(retry, () -> failFastWhenOpen(guarded)).get();
    }

    /** A connection error, timeout or upstream 5xx: worth trying again. */
    static boolean isTransient(Throwable failure) {
        Throwable cause = failure instanceof FeedUnavailableException ? failure.getCause() : null;
        return cause instanceof ResourceAccessException || cause instanceof HttpServerErrorException;
    }

    private static <T> T failFastWhenOpen(Supplier<T> guarded) {
        try {
            return guarded.get();
        } catch (CallNotPermittedException e) {
            throw new FeedUnavailableException(e);
        }
    }

    private static Retry retry(RetrySettings settings) {
        Retry retry = Retry.of(NAME, RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(settings.initialBackoff(), settings.multiplier()))
                .retryOnException(FeedResilience::isTransient)
                .build());
        // The reason is logged as text: a Throwable passed last would be treated as the exception to log, not as {}.
        retry.getEventPublisher().onRetry(event -> log.warn("Retrying the feed call (retry {}) in {}: {}",
                event.getNumberOfRetryAttempts(), event.getWaitInterval(),
                String.valueOf(event.getLastThrowable().getCause())));
        return retry;
    }

    private static CircuitBreaker circuitBreaker(CircuitBreakerSettings settings) {
        CircuitBreaker breaker = CircuitBreaker.of(NAME, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.openDuration())
                .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                .build());
        breaker.getEventPublisher().onStateTransition(event ->
                log.warn("Feed circuit breaker: {}", event.getStateTransition()));
        return breaker;
    }
}
