package com.eatclub.deals.error;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of every error response: {@code {"error": {...}}}. */
@Schema(description = "Error response")
public record ApiErrorResponse(

        @Schema(description = "What went wrong")
        ErrorDetail error) {

    /** The error body for a code; {@code param} may be {@code null} when no request parameter is involved. */
    public static ApiErrorResponse of(ErrorCode code, String message, String param) {
        return new ApiErrorResponse(ErrorDetail.of(code, message, param));
    }
}
