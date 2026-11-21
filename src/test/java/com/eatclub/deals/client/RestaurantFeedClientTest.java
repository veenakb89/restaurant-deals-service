package com.eatclub.deals.client;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the client against a tiny local HTTP server, so no network access is needed. */
class RestaurantFeedClientTest {

    private HttpServer server;
    private int statusToReturn;
    private String bodyToReturn;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/feed.json", exchange -> {
            byte[] body = bodyToReturn.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusToReturn, body.length);
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
        respondWith(200, "{\"restaurants\":[{\"objectId\":\"r1\",\"name\":\"Test\"}]}");

        assertThat(client().fetch().restaurants()).hasSize(1);
    }

    @Test
    void wrapsErrorStatusInFeedUnavailableException() {
        respondWith(500, "{}");

        assertThatThrownBy(() -> client().fetch()).isInstanceOf(FeedUnavailableException.class);
    }

    @Test
    void wrapsInvalidJsonInFeedUnavailableException() {
        respondWith(200, "not json");

        assertThatThrownBy(() -> client().fetch()).isInstanceOf(FeedUnavailableException.class);
    }

    private void respondWith(int status, String body) {
        this.statusToReturn = status;
        this.bodyToReturn = body;
    }

    private RestaurantFeedClient client() {
        String url = "http://localhost:" + server.getAddress().getPort() + "/feed.json";
        return new RestaurantFeedClient(RestClient.builder(), url, Duration.ofSeconds(2), Duration.ofSeconds(2));
    }
}
