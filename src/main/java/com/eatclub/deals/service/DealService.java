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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
        return availableDeals().stream()
                .filter(d -> d.isActiveAt(time))
                .map(d -> ActiveDeal.from(d.restaurant(), d.deal()))
                .toList();
    }

    /**
     * The time window during which the most deals are active, using the same rules as {@link #findActiveDeals}.
     * <ul>
     *   <li>The number of active deals can only change at an opening or closing time, so the count is taken at
     *       each of those times; it stays the same until the next one.</li>
     *   <li>Neighbouring stretches with the same highest count are merged, including across midnight.</li>
     *   <li>If several separate windows tie, the one that starts earliest in the day (from midnight) is returned.</li>
     *   <li>If the highest count lasts all day, {@link TimeWindow#ALL_DAY} is returned.</li>
     * </ul>
     *
     * @return the peak window, or empty if no deal is ever active
     */
    public Optional<TimeWindow> findPeakTime() {
        List<ScheduledDeal> deals = availableDeals();
        List<LocalTime> boundaries = deals.stream()
                .flatMap(ScheduledDeal::boundaries)
                .distinct()
                .sorted()
                .toList();

        // counts[i] is the number of active deals from boundaries[i] until the next boundary.
        int n = boundaries.size();
        int[] counts = new int[n];
        for (int i = 0; i < n; i++) {
            LocalTime time = boundaries.get(i);
            counts[i] = (int) deals.stream().filter(d -> d.isActiveAt(time)).count();
        }
        int peak = Arrays.stream(counts).max().orElse(0);
        if (peak == 0) {
            return Optional.empty();
        }

        // Boundaries are sorted, so the first peak stretch that follows a lower one starts earliest in the day.
        for (int i = 0; i < n; i++) {
            boolean startsAPeak = counts[i] == peak && counts[(i + n - 1) % n] < peak;
            if (startsAPeak) {
                int next = (i + 1) % n;
                while (counts[next] == peak) {
                    next = (next + 1) % n;
                }
                return Optional.of(new TimeWindow(boundaries.get(i), boundaries.get(next)));
            }
        }
        return Optional.of(TimeWindow.ALL_DAY); // the count is at its peak all day
    }

    /** Every scheduled deal that still has quantity left, in source order. */
    private List<ScheduledDeal> availableDeals() {
        return scheduledDeals(feedClient.fetch()).stream()
                .filter(d -> hasQuantityLeft(d.deal()))
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

        /** The only times at which this deal can become active or inactive. */
        Stream<LocalTime> boundaries() {
            return Stream.of(restaurantHours.start(), restaurantHours.end(), dealHours.start(), dealHours.end());
        }
    }
}
