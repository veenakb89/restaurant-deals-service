package com.eatclub.deals.client;

import com.eatclub.deals.client.model.RestaurantFeed;
import com.eatclub.deals.exception.FeedUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches the restaurant feed (restaurants and their deals) from the upstream URL, with a short cache, retry and a
 * circuit breaker.
 */
@Component
public class RestaurantFeedClient {

    /** Name of the cache that holds the last successful feed. */
    public static final String CACHE = "restaurantFeed";

    /** The cache's single key (a SpEL string literal): there is one feed, so there is one entry. */
    private static final String KEY = "'feed'";

    private static final Logger log = LoggerFactory.getLogger(RestaurantFeedClient.class);

    private final RestClient restClient;
    private final FeedResilience resilience;

    public RestaurantFeedClient(RestClient.Builder builder, RestaurantFeedProperties properties,
                                FeedResilience resilience) {
        this.restClient = builder.baseUrl(properties.url())
                .requestFactory(requestFactoryWithTimeouts(properties))
                .build();
        this.resilience = resilience;
        log.info("Restaurant feed: url={}, connectTimeout={}, readTimeout={}, retry={}, circuitBreaker={}",
                properties.url(), properties.connectTimeout(), properties.readTimeout(),
                properties.retry(), properties.circuitBreaker());
    }

    /**
     * The feed, served from the {@value #CACHE} cache while it's fresh (see {@code spring.cache.caffeine.spec}).
     * On a miss, upstream is called with retry and the circuit breaker; only one caller loads at a time
     * ({@code sync}), and failures aren't cached.
     *
     * @throws FeedUnavailableException if the feed is unreachable, times out, returns an error status or invalid JSON
     *                                  (after any retries), or the circuit breaker is open
     */
    @Cacheable(cacheNames = CACHE, key = KEY, sync = true)
    public RestaurantFeed fetch() {
        return resilience.call(this::fetchOnce);
    }

    /** A single call to upstream, without retry. */
    private RestaurantFeed fetchOnce() {
        long start = System.nanoTime();
        try {
            RestaurantFeed body = restClient.get().retrieve().body(RestaurantFeed.class);
            RestaurantFeed feed = body == null ? new RestaurantFeed(null) : body;
            log.debug("Fetched the feed in {} ms: {} restaurants", elapsedMillis(start), feed.restaurants().size());
            return feed;
        } catch (RestClientException e) {
            throw new FeedUnavailableException(e);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static SimpleClientHttpRequestFactory requestFactoryWithTimeouts(RestaurantFeedProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
