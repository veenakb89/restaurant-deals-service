package com.eatclub.deals.client;

import com.eatclub.deals.client.model.RestaurantFeed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Fetches the restaurant feed (restaurants and their deals) from the upstream URL. */
@Component
public class RestaurantFeedClient {

    private final RestClient restClient;

    public RestaurantFeedClient(
            RestClient.Builder builder,
            @Value("${restaurant-feed.url}") String feedUrl,
            @Value("${restaurant-feed.connect-timeout:5s}") Duration connectTimeout,
            @Value("${restaurant-feed.read-timeout:10s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = builder.baseUrl(feedUrl).requestFactory(requestFactory).build();
    }

    public RestaurantFeed fetch() {
        RestaurantFeed feed = restClient.get().retrieve().body(RestaurantFeed.class);
        return feed == null ? new RestaurantFeed(null) : feed;
    }
}
