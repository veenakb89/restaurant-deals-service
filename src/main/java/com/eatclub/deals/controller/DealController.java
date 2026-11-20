package com.eatclub.deals.controller;

import com.eatclub.deals.dto.DealsResponse;
import com.eatclub.deals.service.DealService;
import com.eatclub.deals.util.TimeOfDay;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Deals", description = "Restaurant deals")
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    /** Task 1: e.g. {@code GET /api/deals?timeOfDay=3:00pm}. */
    @GetMapping("/deals")
    @Operation(summary = "List deals active at a time of day",
            description = "Returns every deal that is available at the given time. A deal is only active "
                    + "while its restaurant is open; opening times are inclusive and closing times exclusive.")
    @ApiResponse(responseCode = "200", description = "Matching deals (may be empty)")
    @ApiResponse(responseCode = "400", description = "timeOfDay is missing or not a valid time",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "The upstream deal data source is unavailable",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public DealsResponse getActiveDeals(
            @Parameter(description = "12-hour (3:00pm, 3pm) or 24-hour (15:00) time", example = "3:00pm")
            @RequestParam String timeOfDay) {
        return new DealsResponse(dealService.findActiveDeals(TimeOfDay.parse(timeOfDay)));
    }
}
