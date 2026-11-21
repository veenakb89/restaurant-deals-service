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
 *   <li>A deal's hours must have both {@code open} and {@code close}, or neither. A deal with only one of them
 *       (or a blank one) is logged and runs for the restaurant's hours, like a deal with no hours. This is a
 *       temporary assumption until the product owner confirms the rule.</li>
 *   <li>A deal is only available while the restaurant is open, so a deal's own hours are
 *       intersected with the restaurant's hours (e.g. Vrindavan's 3pm–9pm deal is only
 *       redeemable 6pm–9pm, when the restaurant is open).</li>
 *   <li>Windows are start-inclusive, end-exclusive (see {@link TimeWindow}).</li>
 *   <li>A sold-out deal ({@code qtyLeft} of 0 or less) is not active.</li>
 * </ul>
 */
@Service
public class DealService {

    private static final Logger log = LoggerFactory.getLogger(DealService.class);

    private final RestaurantFeedClient feedClient;

    public DealService(RestaurantFeedClient feedClient) {
        this.feedClient = feedClient;
    }

    /** All deals active at the given time that still have quantity left, in source order. */
    public List<ActiveDeal> findActiveDeals(LocalTime time) {
        return scheduledDeals(feedClient.fetch()).stream()
                .filter(d -> d.isActiveAt(time))
                .filter(d -> hasQuantityLeft(d.deal()))
                .map(d -> ActiveDeal.from(d.restaurant(), d.deal()))
                .toList();
    }

    /**
     * A deal is sold out when {@code qtyLeft} is 0 or less. When the quantity is missing or not a number,
     * the source hasn't said it's sold out, so the deal is kept.
     */
    private static boolean hasQuantityLeft(Deal deal) {
        String qtyLeft = deal.qtyLeft();
        if (qtyLeft == null || qtyLeft.isBlank()) {
            return true;
        }
        try {
            return Integer.parseInt(qtyLeft.trim()) > 0;
        } catch (NumberFormatException e) {
            log.warn("Deal {} has a non-numeric qtyLeft '{}'; keeping it", deal.objectId(), qtyLeft);
            return true;
        }
    }

    private List<ScheduledDeal> scheduledDeals(RestaurantFeed feed) {
        List<ScheduledDeal> result = new ArrayList<>();
        for (Restaurant restaurant : feed.restaurants()) {
            for (Deal deal : restaurant.deals()) {
                try {
                    TimeWindow restaurantHours = windowOrAllDay(restaurant.open(), restaurant.close());
                    TimeWindow dealHours = dealHours(restaurant, deal, restaurantHours);
                    result.add(new ScheduledDeal(restaurant, deal, restaurantHours, dealHours));
                } catch (InvalidTimeOfDayException e) {
                    // Skip a single malformed record rather than failing the whole request.
                    log.warn("Skipping deal {} of restaurant {}: {}", deal.objectId(), restaurant.objectId(), e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * The deal's own hours when it has both {@code open} and {@code close}; otherwise the restaurant's hours.
     * Partial hours (only one of them, or a blank one) are ignored with a warning.
     */
    private static TimeWindow dealHours(Restaurant restaurant, Deal deal, TimeWindow restaurantHours) {
        if (hasHours(deal.open(), deal.close())) {
            return TimeWindow.parse(deal.open(), deal.close());
        }
        if (isPresent(deal.open()) || isPresent(deal.close())) {
            log.warn("Deal {} of restaurant {} has partial hours (open='{}', close='{}'); using the restaurant's hours",
                    deal.objectId(), restaurant.objectId(), deal.open(), deal.close());
        }
        return restaurantHours;
    }

    private static TimeWindow windowOrAllDay(String open, String close) {
        return hasHours(open, close) ? TimeWindow.parse(open, close) : TimeWindow.ALL_DAY;
    }

    private static boolean hasHours(String open, String close) {
        return isPresent(open) && isPresent(close);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private record ScheduledDeal(Restaurant restaurant, Deal deal, TimeWindow restaurantHours, TimeWindow dealHours) {

        boolean isActiveAt(LocalTime time) {
            return restaurantHours.contains(time) && dealHours.contains(time);
        }
    }
}
