package com.eatclub.deals.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

/** What went wrong. The HTTP status is in the response status line, not in the body. */
@Schema(description = "Details of the error")
@JsonPropertyOrder({"type", "code", "message", "param"})
public record ErrorDetail(

        @Schema(description = "Broad category of the error", example = "invalid_request_error")
        ErrorType type,

        @Schema(description = "Stable, machine-readable error code. Clients should branch on this field.",
                example = "invalid_time_of_day")
        ErrorCode code,

        @Schema(description = "Human-readable explanation. May change; don't parse it.",
                example = "Invalid time of day: '25:00'. Expected a time such as '3:00pm', '10:30AM' or '15:00'.")
        String message,

        @Schema(description = "The request parameter that caused the error. Present only for parameter errors.",
                example = "timeOfDay", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String param) {

    /** Builds the detail for a code; {@code param} may be {@code null} when no request parameter is involved. */
    public static ErrorDetail of(ErrorCode code, String message, String param) {
        return new ErrorDetail(code.type(), code, message, param);
    }
}
