package com.eatclub.deals;

import com.eatclub.deals.client.model.RestaurantFeed;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Test data: the saved copy of the upstream feed, and parsing of small inline feeds. */
public final class TestFeeds {

    /** Snapshot of https://eccdn.com.au/misc/challengedata.json, so tests don't need network access. */
    public static final String CHALLENGE_DATA = "/challengedata.json";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TestFeeds() {
    }

    /** The saved challenge feed from {@code src/test/resources/challengedata.json}. */
    public static RestaurantFeed challengeData() {
        try (InputStream in = TestFeeds.class.getResourceAsStream(CHALLENGE_DATA)) {
            if (in == null) {
                throw new IllegalStateException("Test resource not found: " + CHALLENGE_DATA);
            }
            return JSON.readValue(in, RestaurantFeed.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses a feed written inline in a test, exactly as the client would parse the upstream JSON. */
    public static RestaurantFeed fromJson(String json) {
        return JSON.readValue(json, RestaurantFeed.class);
    }
}
