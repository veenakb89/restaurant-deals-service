package com.eatclub.deals.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Response body of {@code GET /api/v1/deals}: the deals active at the requested time. */
@Schema(description = "The deals active at the requested time, in source order")
public record ActiveDealsResponse(

        @Schema(description = "Active deals; empty when nothing is active")
        List<ActiveDeal> deals) {
}
