package com.eatclub.deals.controller;

import com.eatclub.deals.dto.ActiveDealsResponse;
import com.eatclub.deals.dto.ApiErrorResponse;
import com.eatclub.deals.dto.PeakTimeResponse;
import com.eatclub.deals.service.DealService;
import com.eatclub.deals.time.TimeOfDayParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Deals", description = "Restaurant deals")
public class DealController {

    private static final String TIME_OF_DAY = "timeOfDay";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    /** Task 1: e.g. {@code GET /api/deals?timeOfDay=3:00pm}. */
    @GetMapping(value = "/deals", produces = JSON)
    @Operation(summary = "List deals active at a time of day",
            description = "Returns every deal that is available at the given time. A deal is only active "
                    + "while its restaurant is open; opening times are inclusive and closing times exclusive.")
    @ApiResponse(responseCode = "200", description = "Matching deals (may be empty)")
    @ApiResponse(responseCode = "400",
            description = "`timeOfDay` is not a valid time (`invalid_time_of_day`) or is missing (`missing_parameter`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "invalid_time_of_day", value = """
                                    {
                                      "error": {
                                        "type": "invalid_request_error",
                                        "code": "invalid_time_of_day",
                                        "message": "Invalid time of day: '25:00'. Expected a time such as '3:00pm', '10:30AM' or '15:00'.",
                                        "param": "timeOfDay"
                                      }
                                    }"""),
                            @ExampleObject(name = "missing_parameter", value = """
                                    {
                                      "error": {
                                        "type": "invalid_request_error",
                                        "code": "missing_parameter",
                                        "message": "Required parameter 'timeOfDay' is not present.",
                                        "param": "timeOfDay"
                                      }
                                    }""")
                    }))
    @ApiResponse(responseCode = "500", description = "An unexpected error occurred (`internal_error`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "internal_error", value = """
                            {
                              "error": {
                                "type": "api_error",
                                "code": "internal_error",
                                "message": "An unexpected error occurred."
                              }
                            }""")))
    @ApiResponse(responseCode = "502", description = "The upstream deal data source is unavailable (`feed_unavailable`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "feed_unavailable", value = """
                            {
                              "error": {
                                "type": "api_error",
                                "code": "feed_unavailable",
                                "message": "Deal data source is currently unavailable."
                              }
                            }""")))
    public ActiveDealsResponse getActiveDeals(
            @Parameter(description = "12-hour time with am/pm and no space (3:00pm, 10:30AM), or 24-hour time (15:00). "
                    + "Hour and minutes are separated by ':'; leading and trailing spaces are ignored.", example = "3:00pm")
            @RequestParam(TIME_OF_DAY) String timeOfDay) {
        return new ActiveDealsResponse(dealService.findActiveDeals(TimeOfDayParser.parse(timeOfDay, TIME_OF_DAY)));
    }

    /** Task 2: {@code GET /api/deals/peak-time}. */
    @GetMapping(value = "/deals/peak-time", produces = JSON)
    @Operation(summary = "Find the peak time window",
            description = "Returns the time window during which the most deals are active, using the same rules as "
                    + "`GET /api/deals`. If several separate windows tie, the one starting earliest in the day is "
                    + "returned. Equal start and end mean all day; both are null when no deal is ever active.")
    @ApiResponse(responseCode = "200", description = "The peak window (both fields null when no deal is ever active)")
    @ApiResponse(responseCode = "500", description = "An unexpected error occurred (`internal_error`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "internal_error", value = """
                            {
                              "error": {
                                "type": "api_error",
                                "code": "internal_error",
                                "message": "An unexpected error occurred."
                              }
                            }""")))
    @ApiResponse(responseCode = "502", description = "The upstream deal data source is unavailable (`feed_unavailable`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "feed_unavailable", value = """
                            {
                              "error": {
                                "type": "api_error",
                                "code": "feed_unavailable",
                                "message": "Deal data source is currently unavailable."
                              }
                            }""")))
    public PeakTimeResponse getPeakTime() {
        return dealService.findPeakTime().map(PeakTimeResponse::from).orElse(PeakTimeResponse.NONE);
    }
}
