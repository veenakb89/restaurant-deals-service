package com.eatclub.deals.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidTimeOfDayException.class)
    public ProblemDetail handleInvalidTime(InvalidTimeOfDayException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail handleUpstreamFailure(RestClientException e) {
        log.error("Failed to fetch deal data from upstream source", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Deal data source is currently unavailable.");
    }
}
