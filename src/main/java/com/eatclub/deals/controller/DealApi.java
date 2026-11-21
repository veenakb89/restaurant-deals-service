package com.eatclub.deals.controller;

import com.eatclub.deals.config.OpenApiConfig;
import com.eatclub.deals.dto.ActiveDealsResponse;
import com.eatclub.deals.dto.PeakTimeResponse;
import com.eatclub.deals.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The deals endpoints: their routes and their API documentation. {@link DealController} implements them, so the
 * controller holds only the code. Both endpoints need a JWT bearer token with the {@code deals:read} scope.
 */
@RequestMapping(DealApi.BASE_PATH)
@Tag(name = "Deals", description = "Restaurant deals")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public interface DealApi {

    /**
     * Version 1 of the API. The version is in the path, so a future breaking change can be served as {@code /api/v2}
     * alongside v1 while clients move over.
     */
    String BASE_PATH = "/api/v1";
    String TIME_OF_DAY = "timeOfDay";
    String JSON = MediaType.APPLICATION_JSON_VALUE;
    String UNAUTHORIZED_DESCRIPTION = "The access token is missing, invalid or expired (`unauthorized`)";
    String FORBIDDEN_DESCRIPTION = "The access token doesn't have the `deals:read` scope (`forbidden`)";

    /** Task 1: e.g. {@code GET /api/v1/deals?timeOfDay=3:00pm}. */
    @GetMapping(value = "/deals", produces = JSON)
    @Operation(summary = "List deals active at a time of day",
            description = "Returns every deal that is available at the given time. A deal is only active "
                    + "while its restaurant is open; opening times are inclusive and closing times exclusive.")
    @ApiResponse(responseCode = "200", description = "Matching deals (may be empty)")
    @ApiResponse(responseCode = "400",
            description = "`timeOfDay` is not a valid time (`invalid_time_of_day`) or is missing (`missing_parameter`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "invalid_time_of_day", value = ApiErrorExamples.INVALID_TIME_OF_DAY),
                            @ExampleObject(name = "missing_parameter", value = ApiErrorExamples.MISSING_PARAMETER)
                    }))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED_DESCRIPTION,
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "unauthorized", value = ApiErrorExamples.UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "forbidden", value = ApiErrorExamples.FORBIDDEN)))
    @ApiResponse(responseCode = "500", description = "An unexpected error occurred (`internal_error`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "internal_error", value = ApiErrorExamples.INTERNAL_ERROR)))
    @ApiResponse(responseCode = "502", description = "The upstream deal data source is unavailable (`feed_unavailable`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "feed_unavailable", value = ApiErrorExamples.FEED_UNAVAILABLE)))
    ActiveDealsResponse getActiveDeals(
            @Parameter(description = "12-hour time with am/pm and no space (3:00pm, 10:30AM), or 24-hour time (15:00). "
                    + "Hour and minutes are separated by ':'; leading and trailing spaces are ignored.", example = "3:00pm")
            @RequestParam(TIME_OF_DAY) String timeOfDay);

    /** Task 2: {@code GET /api/v1/deals/peak-time}. */
    @GetMapping(value = "/deals/peak-time", produces = JSON)
    @Operation(summary = "Find the peak time window",
            description = "Returns the time window during which the most deals are active, using the same rules as "
                    + "`GET /api/v1/deals`. If several separate windows tie, the one starting earliest in the day is "
                    + "returned. Equal start and end mean all day; both are null when no deal is ever active.")
    @ApiResponse(responseCode = "200", description = "The peak window (both fields null when no deal is ever active)")
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED_DESCRIPTION,
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "unauthorized", value = ApiErrorExamples.UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "forbidden", value = ApiErrorExamples.FORBIDDEN)))
    @ApiResponse(responseCode = "500", description = "An unexpected error occurred (`internal_error`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "internal_error", value = ApiErrorExamples.INTERNAL_ERROR)))
    @ApiResponse(responseCode = "502", description = "The upstream deal data source is unavailable (`feed_unavailable`)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(name = "feed_unavailable", value = ApiErrorExamples.FEED_UNAVAILABLE)))
    PeakTimeResponse getPeakTime();
}
