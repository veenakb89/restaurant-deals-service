package com.eatclub.deals.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Binds the {@code restaurant-feed.*} properties the same way the app does at startup. */
class RestaurantFeedPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void timeoutsDefaultTo2And5Seconds() {
        runner.withPropertyValues("restaurant-feed.url=https://example.com/feed.json")
                .run(context -> {
                    RestaurantFeedProperties properties = context.getBean(RestaurantFeedProperties.class);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Test
    void retryAndCircuitBreakerHaveDefaults() {
        runner.withPropertyValues("restaurant-feed.url=https://example.com/feed.json")
                .run(context -> {
                    RestaurantFeedProperties properties = context.getBean(RestaurantFeedProperties.class);
                    assertThat(properties.retry()).isEqualTo(
                            new RestaurantFeedProperties.RetrySettings(3, Duration.ofMillis(200), 2.0));
                    assertThat(properties.circuitBreaker()).isEqualTo(
                            new RestaurantFeedProperties.CircuitBreakerSettings(50, 10, 5, Duration.ofSeconds(30), 2));
                });
    }

    @Test
    void appFailsToStartWithAnOutOfRangeResilienceSetting() {
        runner.withPropertyValues("restaurant-feed.url=https://example.com/feed.json",
                        "restaurant-feed.retry.max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void appFailsToStartWithoutAUrl() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest(name = "url=''{0}''")
    @ValueSource(strings = {"", " "})
    void appFailsToStartWithABlankUrl(String url) {
        runner.withPropertyValues("restaurant-feed.url=" + url)
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(RestaurantFeedProperties.class)
    static class Config {
    }
}
