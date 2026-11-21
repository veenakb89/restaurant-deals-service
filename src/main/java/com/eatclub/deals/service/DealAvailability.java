package com.eatclub.deals.service;

import com.eatclub.deals.client.model.Deal;
import com.eatclub.deals.client.model.Restaurant;
import com.eatclub.deals.time.TimeWindow;

import java.time.LocalTime;
import java.util.stream.Stream;

/** A deal from the feed together with the hours that decide when it's available. */
record DealAvailability(Restaurant restaurant, Deal deal, TimeWindow restaurantHours, TimeWindow dealHours) {

    /** A deal is active only while both the deal and its restaurant are open. */
    boolean isActiveAt(LocalTime time) {
        return restaurantHours.contains(time) && dealHours.contains(time);
    }

    /** The only times at which this deal can become active or inactive. */
    Stream<LocalTime> boundaries() {
        return Stream.of(restaurantHours.start(), restaurantHours.end(), dealHours.start(), dealHours.end());
    }
}
