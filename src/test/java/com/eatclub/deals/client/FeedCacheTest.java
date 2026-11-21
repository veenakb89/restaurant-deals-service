package com.eatclub.deals.client;

import com.eatclub.deals.exception.FeedUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The real client bean, with its cache, against a local server that counts upstream calls. */
@SpringBootTest(properties = {
        "spring.cache.caffeine.spec=maximumSize=1,expireAfterWrite=1s", // Caffeine's spec has no "ms" unit
        "restaurant-feed.retry.initial-backoff=1ms",
        // The circuit breaker is shared by these tests; keep it closed so only the cache is being tested.
        "restaurant-feed.circuit-breaker.sliding-window-size=100",
        "restaurant-feed.circuit-breaker.minimum-calls=100"})
class FeedCacheTest {

    private static final String FEED = "{\"restaurants\":[{\"objectId\":\"r1\",\"name\":\"Test\"}]}";
    private static final AtomicInteger hits = new AtomicInteger();
    private static volatile int status = 200;
    private static final HttpServer server = startServer();

    @Autowired
    private RestaurantFeedClient client;

    @Autowired
    private CacheManager cacheManager;

    @DynamicPropertySource
    static void feedUrl(DynamicPropertyRegistry registry) {
        registry.add("restaurant-feed.url", () -> "http://localhost:" + server.getAddress().getPort() + "/feed.json");
    }

    @BeforeEach
    void reset() {
        cacheManager.getCache(RestaurantFeedClient.CACHE).clear();
        hits.set(0);
        status = 200;
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void repeatedFetchesWithinTheTtlCallUpstreamOnce() {
        client.fetch();
        client.fetch();
        client.fetch();

        assertThat(hits).hasValue(1);
    }

    @Test
    void anExpiredEntryIsFetchedAgain() throws InterruptedException {
        client.fetch();
        Thread.sleep(1200); // longer than the 1s test TTL
        client.fetch();

        assertThat(hits).hasValue(2);
    }

    @Test
    void failuresAreNotCached() {
        status = 500;
        assertThatThrownBy(() -> client.fetch()).isInstanceOf(FeedUnavailableException.class);
        int hitsWhileFailing = hits.get(); // includes the retries

        status = 200;
        assertThat(client.fetch().restaurants()).hasSize(1);
        assertThat(hits).hasValue(hitsWhileFailing + 1);
    }

    private static HttpServer startServer() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            httpServer.createContext("/feed.json", exchange -> {
                hits.incrementAndGet();
                byte[] body = FEED.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            httpServer.start();
            return httpServer;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
