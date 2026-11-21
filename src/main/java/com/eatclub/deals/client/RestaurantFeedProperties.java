package com.eatclub.deals.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for the upstream restaurant feed, from the {@code restaurant-feed.*} properties.
 * The app refuses to start if {@code url} is missing or blank, or a resilience setting is out of range.
 */
@Validated
@ConfigurationProperties("restaurant-feed")
public record RestaurantFeedProperties(
        @NotBlank String url,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue @Valid RetrySettings retry,
        @DefaultValue @Valid CircuitBreakerSettings circuitBreaker) {

    /** {@code restaurant-feed.retry.*}: how often a failed feed call is retried, and how long to wait in between. */
    public record RetrySettings(
            @DefaultValue("3") @Min(1) int maxAttempts,
            @DefaultValue("200ms") Duration initialBackoff,
            @DefaultValue("2") @DecimalMin("1.0") double multiplier) {
    }

    /** {@code restaurant-feed.circuit-breaker.*}: when to stop calling a failing feed, and for how long. */
    public record CircuitBreakerSettings(
            @DefaultValue("50") @Min(1) @Max(100) float failureRateThreshold,
            @DefaultValue("10") @Min(1) int slidingWindowSize,
            @DefaultValue("5") @Min(1) int minimumCalls,
            @DefaultValue("30s") Duration openDuration,
            @DefaultValue("2") @Min(1) int halfOpenCalls) {
    }
}
