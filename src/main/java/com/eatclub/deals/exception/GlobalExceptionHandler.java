package com.eatclub.deals.exception;

import com.eatclub.deals.dto.ApiErrorResponse;
import com.eatclub.deals.dto.ErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The single place where every error becomes an HTTP response.
 * <p>
 * Every error has the same body, {@code {"error": {"type", "code", "message", "param"}}} ({@link ApiErrorResponse}),
 * and the HTTP status is set on the response. Spring MVC's own exceptions (missing parameter, unknown URL,
 * wrong method, ...) are routed through {@link #handleExceptionInternal} and converted to the same body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTimeOfDayException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTimeOfDay(InvalidTimeOfDayException e) {
        return error(ErrorCode.INVALID_TIME_OF_DAY, e.getMessage(), e.parameter());
    }

    @ExceptionHandler(FeedUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleFeedUnavailable(FeedUnavailableException e) {
        log.error("Failed to fetch deal data from upstream source", e);
        return error(ErrorCode.FEED_UNAVAILABLE, "Deal data source is currently unavailable.", null);
    }

    /** Catch-all: never expose internal details to the client, but log everything. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", null);
    }

    /** Converts the errors Spring MVC itself raises into the same body, keeping Spring's status and headers. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = errorCodeFor(ex, statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("Request failed", ex);
        }
        String param = ex instanceof MissingServletRequestParameterException missing ? missing.getParameterName() : null;
        ApiErrorResponse response = new ApiErrorResponse(ErrorDetail.of(code, messageFor(ex, statusCode), param));
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    private static ErrorCode errorCodeFor(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof MissingServletRequestParameterException) {
            return ErrorCode.MISSING_PARAMETER;
        }
        if (ex instanceof NoResourceFoundException) {
            return ErrorCode.NOT_FOUND;
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        return statusCode.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
    }

    /** Spring's own message for client errors; a generic one for server errors, so internals never leak. */
    private static String messageFor(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof NoResourceFoundException) {
            return "No endpoint found at this URL.";
        }
        if (statusCode.is4xxClientError() && ex instanceof ErrorResponse errorResponse
                && errorResponse.getBody().getDetail() != null) {
            return errorResponse.getBody().getDetail();
        }
        return statusCode.is4xxClientError() ? "The request is invalid." : "An unexpected error occurred.";
    }

    private static ResponseEntity<ApiErrorResponse> error(ErrorCode code, String message, String param) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(ErrorDetail.of(code, message, param)));
    }
}
