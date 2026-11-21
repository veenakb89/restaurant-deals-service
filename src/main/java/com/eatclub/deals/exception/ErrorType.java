package com.eatclub.deals.exception;

import com.fasterxml.jackson.annotation.JsonValue;

/** Broad category of an error, returned as {@code error.type}. */
public enum ErrorType {

    /** The request was invalid: bad or missing input, unknown URL, wrong HTTP method. */
    INVALID_REQUEST_ERROR("invalid_request_error"),

    /** Something went wrong on our side or in a service we depend on. */
    API_ERROR("api_error");

    private final String value;

    ErrorType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
