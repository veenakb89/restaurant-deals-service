package com.eatclub.deals.error;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes, returned as {@code error.code}.
 * <p>
 * Clients should branch on the code, never on the human-readable message.
 * Codes are part of the API contract: once published, a code is never renamed.
 * Each code has a default message, used when there's no more specific one.
 */
public enum ErrorCode {

    INVALID_TIME_OF_DAY("invalid_time_of_day", HttpStatus.BAD_REQUEST, ErrorType.INVALID_REQUEST_ERROR,
            "The time of day is invalid."),
    MISSING_PARAMETER("missing_parameter", HttpStatus.BAD_REQUEST, ErrorType.INVALID_REQUEST_ERROR,
            "A required parameter is missing."),
    BAD_REQUEST("bad_request", HttpStatus.BAD_REQUEST, ErrorType.INVALID_REQUEST_ERROR,
            "The request is invalid."),
    NOT_FOUND("not_found", HttpStatus.NOT_FOUND, ErrorType.INVALID_REQUEST_ERROR,
            "No endpoint found at this URL."),
    METHOD_NOT_ALLOWED("method_not_allowed", HttpStatus.METHOD_NOT_ALLOWED, ErrorType.INVALID_REQUEST_ERROR,
            "The HTTP method is not supported for this URL."),
    FEED_UNAVAILABLE("feed_unavailable", HttpStatus.BAD_GATEWAY, ErrorType.API_ERROR,
            "Deal data source is currently unavailable."),
    INTERNAL_ERROR("internal_error", HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.API_ERROR,
            "An unexpected error occurred.");

    private final String code;
    private final HttpStatus status;
    private final ErrorType type;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus status, ErrorType type, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.type = type;
        this.defaultMessage = defaultMessage;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorType type() {
        return type;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
