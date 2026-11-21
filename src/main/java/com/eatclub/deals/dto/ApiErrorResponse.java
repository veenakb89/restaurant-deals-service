package com.eatclub.deals.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of every error response: {@code {"error": {...}}}. */
@Schema(description = "Error response")
public record ApiErrorResponse(

        @Schema(description = "What went wrong")
        ErrorDetail error) {
}
