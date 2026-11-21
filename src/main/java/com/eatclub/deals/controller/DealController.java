package com.eatclub.deals.controller;

import com.eatclub.deals.dto.ActiveDealsResponse;
import com.eatclub.deals.dto.PeakTimeResponse;
import com.eatclub.deals.service.DealService;
import com.eatclub.deals.time.TimeOfDayParser;
import org.springframework.web.bind.annotation.RestController;

/** Handles the deals endpoints. Routes and API documentation are on {@link DealApi}. */
@RestController
public class DealController implements DealApi {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @Override
    public ActiveDealsResponse getActiveDeals(String timeOfDay) {
        return new ActiveDealsResponse(dealService.findActiveDeals(TimeOfDayParser.parse(timeOfDay, TIME_OF_DAY)));
    }

    @Override
    public PeakTimeResponse getPeakTime() {
        return dealService.findPeakTime().map(PeakTimeResponse::fromWindow).orElse(PeakTimeResponse.NO_PEAK);
    }
}
