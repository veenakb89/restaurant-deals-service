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
    void timeoutsDefaultTo5And10Seconds() {
        runner.withPropertyValues("restaurant-feed.url=https://example.com/feed.json")
                .run(context -> {
                    RestaurantFeedProperties properties = context.getBean(RestaurantFeedProperties.class);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
                });
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
