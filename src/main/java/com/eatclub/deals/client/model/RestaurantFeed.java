package com.eatclub.deals.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Root of the upstream restaurant feed: every restaurant together with its deals. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RestaurantFeed(List<Restaurant> restaurants) {

    public List<Restaurant> restaurants() {
        return restaurants == null ? List.of() : restaurants;
    }
}
