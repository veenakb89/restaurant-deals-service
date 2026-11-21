package com.eatclub.deals.service;

import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.dto.ActiveDeal;
import com.eatclub.deals.time.TimeWindow;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Answers questions about the deals in the restaurant feed. Both questions use the same deals and the same
 * definition of "active" ({@link DealAvailabilityMapper}, {@link DealAvailability#isActiveAt}), so they always agree.
 */
@Service
public class DealService {

    private final RestaurantFeedClient feedClient;

    public DealService(RestaurantFeedClient feedClient) {
        this.feedClient = feedClient;
    }

    /** All deals active at the given time that still have quantity left, in source order. */
    public List<ActiveDeal> findActiveDeals(LocalTime time) {
        return availableDeals().stream()
                .filter(deal -> deal.isActiveAt(time))
                .map(deal -> ActiveDeal.fromFeed(deal.restaurant(), deal.deal()))
                .toList();
    }

    /** The window during which the most deals are active, or empty if no deal is ever active. */
    public Optional<TimeWindow> findPeakTime() {
        return PeakTimeCalculator.findPeak(availableDeals());
    }

    private List<DealAvailability> availableDeals() {
        return DealAvailabilityMapper.fromFeed(feedClient.fetch());
    }
}
