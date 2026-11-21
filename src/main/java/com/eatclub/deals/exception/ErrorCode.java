package com.eatclub.deals.exception;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes, returned as {@code error.code}.
 * <p>
 * Clients should branch on the code, never on the human-readable message.
 * Codes are part of the API contract: once published, a code is never renamed.
 */
public enum ErrorCode {

    INVALID_TIME_OF_DAY("invalid_time_of_day", HttpStatus.BAD_REQUEST, ErrorType.INVALID_REQUEST_ERROR),
    MISSING_PARAMETER("missing_parameter", HttpStatus.BAD_REQUEST, ErrorType.INVALID_REQUEST_ERROR),
    BAD_REQUEST("bad_request", HttpStatus.BAD_REQUEST, ErrorType.INVALID_REQUEST_ERROR),
    NOT_FOUND("not_found", HttpStatus.NOT_FOUND, ErrorType.INVALID_REQUEST_ERROR),
    METHOD_NOT_ALLOWED("method_not_allowed", HttpStatus.METHOD_NOT_ALLOWED, ErrorType.INVALID_REQUEST_ERROR),
    FEED_UNAVAILABLE("feed_unavailable", HttpStatus.BAD_GATEWAY, ErrorType.API_ERROR),
    INTERNAL_ERROR("internal_error", HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.API_ERROR);

    private final String code;
    private final HttpStatus status;
    private final ErrorType type;

    ErrorCode(String code, HttpStatus status, ErrorType type) {
        this.code = code;
        this.status = status;
        this.type = type;
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
}
