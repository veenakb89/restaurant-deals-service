package com.eatclub.deals.client;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for the upstream restaurant feed, from the {@code restaurant-feed.*} properties.
 * The app refuses to start if {@code url} is missing or blank.
 */
@Validated
@ConfigurationProperties("restaurant-feed")
public record RestaurantFeedProperties(
        @NotBlank String url,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout) {
}
