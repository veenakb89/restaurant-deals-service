package com.eatclub.deals.error;

import com.eatclub.deals.exception.FeedCircuitOpenException;
import com.eatclub.deals.exception.FeedUnavailableException;
import com.eatclub.deals.exception.InvalidTimeOfDayException;
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
        return respond(ErrorCode.INVALID_TIME_OF_DAY, e.getMessage(), e.parameter());
    }

    /** Same response either way; an open circuit is expected during an outage, so it's one line, not a stack trace. */
    @ExceptionHandler(FeedUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleFeedUnavailable(FeedUnavailableException e) {
        if (e instanceof FeedCircuitOpenException) {
            log.warn("Feed circuit breaker is open; failing fast without calling upstream");
        } else {
            log.error("Failed to fetch deal data from upstream source", e);
        }
        return respond(ErrorCode.FEED_UNAVAILABLE);
    }

    /** Catch-all: never expose internal details to the client, but log everything. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    /** Converts the errors Spring MVC itself raises into the same body, keeping Spring's status and headers. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        logIfServerError(ex, statusCode);
        ErrorCode code = errorCodeFor(ex, statusCode);
        return jsonResponse(statusCode, headers).body(ApiErrorResponse.of(code, messageFor(ex, code), paramFor(ex)));
    }

    private static void logIfServerError(Exception ex, HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            log.error("Request failed", ex);
        }
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

    /**
     * Spring's own explanation for client errors, when it has one; otherwise the code's default message.
     * Server errors always get the default message, so internals never leak. Not found always uses ours,
     * because Spring's mentions static resources.
     */
    private static String messageFor(Exception ex, ErrorCode code) {
        if (code == ErrorCode.NOT_FOUND || code.type() == ErrorType.API_ERROR) {
            return code.defaultMessage();
        }
        String springDetail = ex instanceof ErrorResponse errorResponse ? errorResponse.getBody().getDetail() : null;
        return springDetail != null ? springDetail : code.defaultMessage();
    }

    /** The request parameter that caused the error, or {@code null} if none did. */
    private static String paramFor(Exception ex) {
        return ex instanceof MissingServletRequestParameterException missing ? missing.getParameterName() : null;
    }

    private static ResponseEntity<ApiErrorResponse> respond(ErrorCode code) {
        return respond(code, code.defaultMessage(), null);
    }

    private static ResponseEntity<ApiErrorResponse> respond(ErrorCode code, String message, String param) {
        return jsonResponse(code.status(), new HttpHeaders()).body(ApiErrorResponse.of(code, message, param));
    }

    /** The one place that sets an error response's status, headers and JSON content type. */
    private static ResponseEntity.BodyBuilder jsonResponse(HttpStatusCode status, HttpHeaders headers) {
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON);
    }
}
