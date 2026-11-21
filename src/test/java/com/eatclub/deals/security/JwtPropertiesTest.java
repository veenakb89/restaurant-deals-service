package com.eatclub.deals.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Binds the {@code security.jwt.*} properties the same way the app does at startup. */
class JwtPropertiesTest {

    private static final String VALID_SECRET = "a-valid-secret-that-is-at-least-32-chars";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "security.jwt.issuer=restaurant-deals",
                    "security.jwt.audience=deals-api",
                    "security.jwt.subject=demo-client",
                    "security.jwt.scope=deals:read",
                    "security.jwt.secret=" + VALID_SECRET);

    @Test
    void tokenLifetimeDefaultsToTwelveHours() {
        runner.run(context ->
                assertThat(context.getBean(JwtProperties.class).tokenTtl()).isEqualTo(Duration.ofHours(12)));
    }

    @ParameterizedTest(name = "secret=''{0}''")
    @ValueSource(strings = {"", " ", "too-short-secret"})
    void appFailsToStartWithABlankOrShortSecret(String secret) {
        runner.withPropertyValues("security.jwt.secret=" + secret)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void appFailsToStartWithABlankIssuer() {
        runner.withPropertyValues("security.jwt.issuer=")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
    }
}
