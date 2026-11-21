package com.eatclub.deals.client;

import com.eatclub.deals.client.model.RestaurantFeed;
import com.eatclub.deals.exception.FeedUnavailableException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Fetches the restaurant feed (restaurants and their deals) from the upstream URL, with retry and a circuit breaker. */
@Component
public class RestaurantFeedClient {

    private final RestClient restClient;
    private final FeedResilience resilience;

    public RestaurantFeedClient(RestClient.Builder builder, RestaurantFeedProperties properties,
                                FeedResilience resilience) {
        this.restClient = builder.baseUrl(properties.url())
                .requestFactory(requestFactoryWithTimeouts(properties))
                .build();
        this.resilience = resilience;
    }

    /**
     * @throws FeedUnavailableException if the feed is unreachable, times out, returns an error status or invalid JSON
     *                                  (after any retries), or the circuit breaker is open
     */
    public RestaurantFeed fetch() {
        return resilience.call(this::fetchOnce);
    }

    /** A single call to upstream, without retry. */
    private RestaurantFeed fetchOnce() {
        try {
            RestaurantFeed feed = restClient.get().retrieve().body(RestaurantFeed.class);
            return feed == null ? new RestaurantFeed(null) : feed;
        } catch (RestClientException e) {
            throw new FeedUnavailableException(e);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactoryWithTimeouts(RestaurantFeedProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
