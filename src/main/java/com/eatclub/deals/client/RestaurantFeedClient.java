package com.eatclub.deals.client;

import com.eatclub.deals.client.model.RestaurantFeed;
import com.eatclub.deals.exception.FeedUnavailableException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Fetches the restaurant feed (restaurants and their deals) from the upstream URL. */
@Component
public class RestaurantFeedClient {

    private final RestClient restClient;

    public RestaurantFeedClient(RestClient.Builder builder, RestaurantFeedProperties properties) {
        this.restClient = builder.baseUrl(properties.url())
                .requestFactory(requestFactoryWithTimeouts(properties))
                .build();
    }

    /**
     * @throws FeedUnavailableException if the feed is unreachable, times out, returns an error status or invalid JSON
     */
    public RestaurantFeed fetch() {
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
