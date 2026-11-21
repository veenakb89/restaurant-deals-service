package com.eatclub.deals.client;

import com.eatclub.deals.client.RestaurantFeedProperties.CircuitBreakerSettings;
import com.eatclub.deals.client.RestaurantFeedProperties.RetrySettings;
import com.eatclub.deals.exception.FeedUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedResilienceTest {

    /** 3 attempts with a 1ms wait, so the tests stay fast. */
    private static final RetrySettings FAST_RETRY = new RetrySettings(3, Duration.ofMillis(1), 1.0);
    private static final RetrySettings NO_RETRY = new RetrySettings(1, Duration.ofMillis(1), 1.0);
    /** Never opens during the retry tests. */
    private static final CircuitBreakerSettings NEVER_OPENS = new CircuitBreakerSettings(100, 100, 100, Duration.ofSeconds(30), 2);
    /** Opens once 2 of the last 4 calls have failed. */
    private static final CircuitBreakerSettings OPENS_QUICKLY = new CircuitBreakerSettings(50, 4, 4, Duration.ofMillis(50), 2);

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void successfulCallRunsOnce() {
        FeedResilience resilience = resilience(FAST_RETRY, NEVER_OPENS);

        assertThat(resilience.call(counting(() -> "feed"))).isEqualTo("feed");
        assertThat(calls).hasValue(1);
    }

    @Test
    void transientFailuresAreRetriedUntilTheCallSucceeds() {
        FeedResilience resilience = resilience(FAST_RETRY, NEVER_OPENS);

        String result = resilience.call(counting(() -> {
            if (calls.get() < 3) {
                throw transientFailure();
            }
            return "feed";
        }));

        assertThat(result).isEqualTo("feed");
        assertThat(calls).hasValue(3);
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttempts() {
        FeedResilience resilience = resilience(FAST_RETRY, NEVER_OPENS);

        assertThatThrownBy(() -> resilience.call(counting(() -> { throw transientFailure(); })))
                .isInstanceOf(FeedUnavailableException.class);
        assertThat(calls).hasValue(3);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("permanentFailures")
    void permanentFailuresAreNotRetried(String description, RuntimeException failure) {
        FeedResilience resilience = resilience(FAST_RETRY, NEVER_OPENS);

        assertThatThrownBy(() -> resilience.call(counting(() -> { throw failure; }))).isSameAs(failure);
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transientFailures")
    void connectionErrorsTimeoutsAndServerErrorsAreTransient(String description, RuntimeException failure) {
        assertThat(FeedResilience.isTransient(failure)).isTrue();
    }

    @Test
    void openCircuitFailsFastWithoutCallingUpstream() {
        FeedResilience resilience = resilience(NO_RETRY, OPENS_QUICKLY);
        failTimes(resilience, 4);

        assertThatThrownBy(() -> resilience.call(counting(() -> "feed")))
                .isInstanceOf(FeedUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        assertThat(calls).as("upstream calls").hasValue(4);
    }

    @Test
    void openCircuitIsNotRetried() {
        FeedResilience resilience = resilience(FAST_RETRY, OPENS_QUICKLY);
        failTimes(resilience, 2); // 2 requests × 3 attempts each: the circuit opens on the 4th failed attempt
        int upstreamCallsSoFar = calls.get();

        assertThatThrownBy(() -> resilience.call(counting(() -> "feed"))).isInstanceOf(FeedUnavailableException.class);
        assertThat(calls).hasValue(upstreamCallsSoFar);
    }

    @Test
    void circuitClosesAgainWhenTrialCallsSucceed() throws InterruptedException {
        FeedResilience resilience = resilience(NO_RETRY, OPENS_QUICKLY);
        failTimes(resilience, 4);

        Thread.sleep(100); // longer than the 50ms open duration, so the next calls are let through as trials
        calls.set(0);
        for (int i = 0; i < 5; i++) {
            assertThat(resilience.call(counting(() -> "feed"))).isEqualTo("feed");
        }
        assertThat(calls).as("all calls reached upstream once the circuit closed").hasValue(5);
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    static Stream<Arguments> transientFailures() {
        return Stream.of(
                Arguments.of("connection error or timeout", transientFailure()),
                Arguments.of("upstream 5xx", new FeedUnavailableException(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))));
    }

    static Stream<Arguments> permanentFailures() {
        return Stream.of(
                Arguments.of("upstream 4xx", new FeedUnavailableException(new HttpClientErrorException(HttpStatus.NOT_FOUND))),
                Arguments.of("invalid JSON", new FeedUnavailableException(new RestClientException("Could not read JSON"))),
                Arguments.of("not a feed failure", new IllegalStateException("bug")));
    }

    private static FeedUnavailableException transientFailure() {
        return new FeedUnavailableException(new ResourceAccessException("Read timed out"));
    }

    private void failTimes(FeedResilience resilience, int requests) {
        for (int i = 0; i < requests; i++) {
            assertThatThrownBy(() -> resilience.call(counting(() -> { throw transientFailure(); })))
                    .isInstanceOf(FeedUnavailableException.class);
        }
    }

    private <T> Supplier<T> counting(Supplier<T> call) {
        return () -> {
            calls.incrementAndGet();
            return call.get();
        };
    }

    private static FeedResilience resilience(RetrySettings retry, CircuitBreakerSettings circuitBreaker) {
        return new FeedResilience(new RestaurantFeedProperties(
                "http://unused", Duration.ofSeconds(2), Duration.ofSeconds(5), retry, circuitBreaker));
    }
}
