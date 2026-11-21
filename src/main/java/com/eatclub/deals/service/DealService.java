package com.eatclub.deals.service;

import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.client.model.Deal;
import com.eatclub.deals.client.model.Restaurant;
import com.eatclub.deals.client.model.RestaurantFeed;
import com.eatclub.deals.dto.ActiveDeal;
import com.eatclub.deals.exception.InvalidTimeOfDayException;
import com.eatclub.deals.time.TimeWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Business rules:
 * <ul>
 *   <li>A deal with no hours of its own runs for the restaurant's opening hours.</li>
 *   <li>A deal is only available while the restaurant is open, so a deal's own hours are
 *       intersected with the restaurant's hours (e.g. Vrindavan's 3pm–9pm deal is only
 *       redeemable 6pm–9pm, when the restaurant is open).</li>
 *   <li>Windows are start-inclusive, end-exclusive (see {@link TimeWindow}).</li>
 * </ul>
 */
@Service
public class DealService {

    private static final Logger log = LoggerFactory.getLogger(DealService.class);

    private final RestaurantFeedClient feedClient;

    public DealService(RestaurantFeedClient feedClient) {
        this.feedClient = feedClient;
    }

    /** All deals active at the given time, in source order. */
    public List<ActiveDeal> findActiveDeals(LocalTime time) {
        return scheduledDeals(feedClient.fetch()).stream()
                .filter(d -> d.isActiveAt(time))
                .map(d -> ActiveDeal.from(d.restaurant(), d.deal()))
                .toList();
    }

    private List<ScheduledDeal> scheduledDeals(RestaurantFeed feed) {
        List<ScheduledDeal> result = new ArrayList<>();
        for (Restaurant restaurant : feed.restaurants()) {
            for (Deal deal : restaurant.deals()) {
                try {
                    TimeWindow restaurantHours = windowOrAllDay(restaurant.open(), restaurant.close());
                    TimeWindow dealHours = hasHours(deal.open(), deal.close())
                            ? TimeWindow.parse(deal.open(), deal.close())
                            : restaurantHours;
                    result.add(new ScheduledDeal(restaurant, deal, restaurantHours, dealHours));
                } catch (InvalidTimeOfDayException e) {
                    // Skip a single malformed record rather than failing the whole request.
                    log.warn("Skipping deal {} of restaurant {}: {}", deal.objectId(), restaurant.objectId(), e.getMessage());
                }
            }
        }
        return result;
    }

    private static TimeWindow windowOrAllDay(String open, String close) {
        return hasHours(open, close) ? TimeWindow.parse(open, close) : TimeWindow.ALL_DAY;
    }

    private static boolean hasHours(String open, String close) {
        return open != null && !open.isBlank() && close != null && !close.isBlank();
    }

    private record ScheduledDeal(Restaurant restaurant, Deal deal, TimeWindow restaurantHours, TimeWindow dealHours) {

        boolean isActiveAt(LocalTime time) {
            return restaurantHours.contains(time) && dealHours.contains(time);
        }
    }
}
