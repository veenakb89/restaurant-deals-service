package com.eatclub.deals.client;

import com.eatclub.deals.client.RestaurantFeedProperties.CircuitBreakerSettings;
import com.eatclub.deals.client.RestaurantFeedProperties.RetrySettings;
import com.eatclub.deals.exception.FeedUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the client against a tiny local HTTP server, so no network access is needed. */
class RestaurantFeedClientTest {

    private static final String FEED = "{\"restaurants\":[{\"objectId\":\"r1\",\"name\":\"Test\"}]}";

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    /** The status for each call in turn; the last one repeats. */
    private int[] statuses;
    private String bodyToReturn;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/feed.json", exchange -> {
            int call = hits.getAndIncrement();
            byte[] body = bodyToReturn.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statuses[Math.min(call, statuses.length - 1)], body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsTheFeed() {
        respondWith(FEED, 200);

        assertThat(client().fetch().restaurants()).hasSize(1);
        assertThat(hits).hasValue(1);
    }

    @Test
    void returnsAnEmptyFeedWhenTheBodyIsEmpty() {
        respondWith("", 200);

        assertThat(client().fetch().restaurants()).isEmpty();
    }

    @Test
    void retriesAServerErrorAndSucceeds() {
        respondWith(FEED, 503, 200);

        assertThat(client().fetch().restaurants()).hasSize(1);
        assertThat(hits).hasValue(2);
    }

    @Test
    void wrapsAPersistentServerErrorInFeedUnavailableExceptionAfterThreeAttempts() {
        respondWith("{}", 500);

        assertThatThrownBy(() -> client().fetch()).isInstanceOf(FeedUnavailableException.class);
        assertThat(hits).hasValue(3);
    }

    @Test
    void doesNotRetryAClientError() {
        respondWith("{}", 404);

        assertThatThrownBy(() -> client().fetch()).isInstanceOf(FeedUnavailableException.class);
        assertThat(hits).hasValue(1);
    }

    @Test
    void doesNotRetryInvalidJson() {
        respondWith("not json", 200);

        assertThatThrownBy(() -> client().fetch()).isInstanceOf(FeedUnavailableException.class);
        assertThat(hits).hasValue(1);
    }

    private void respondWith(String body, int... statuses) {
        this.bodyToReturn = body;
        this.statuses = statuses;
    }

    private RestaurantFeedClient client() {
        String url = "http://localhost:" + server.getAddress().getPort() + "/feed.json";
        var properties = new RestaurantFeedProperties(url, Duration.ofSeconds(2), Duration.ofSeconds(2),
                new RetrySettings(3, Duration.ofMillis(1), 1.0),
                new CircuitBreakerSettings(50, 10, 5, Duration.ofSeconds(30), 2));
        return new RestaurantFeedClient(RestClient.builder(), properties, new FeedResilience(properties));
    }
}
