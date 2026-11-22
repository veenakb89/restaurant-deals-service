package com.eatclub.deals.error;

import com.eatclub.deals.exception.FeedCircuitOpenException;
import com.eatclub.deals.exception.FeedUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the fallback paths of {@link GlobalExceptionHandler#handleExceptionInternal}: Spring MVC errors
 * that have no specific {@link ErrorCode}. The specific cases are covered end-to-end in {@code ApiErrorResponsesIT} (integration tests).
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    @Test
    void openCircuitGivesTheUsualFeedErrorAndLogsOneLineWithoutAStackTrace(CapturedOutput output) {
        var ex = new FeedCircuitOpenException(new IllegalStateException("CircuitBreaker 'restaurantFeed' is OPEN"));

        ResponseEntity<ApiErrorResponse> response = handler.handleFeedUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.FEED_UNAVAILABLE);
        assertThat(output).contains("Feed circuit breaker is open; failing fast");
        assertThat(output).doesNotContain("FeedCircuitOpenException");
    }

    @Test
    void otherFeedFailuresAreLoggedWithTheirStackTrace(CapturedOutput output) {
        handler.handleFeedUnavailable(new FeedUnavailableException(new IllegalStateException("connection refused")));

        assertThat(output).contains("Failed to fetch deal data from upstream source");
        assertThat(output).contains("connection refused");
    }

    @Test
    void otherSpringClientErrorBecomesBadRequestWithSpringsMessage() {
        var ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = handle(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        ErrorDetail error = errorOf(response);
        assertThat(error.type()).isEqualTo(ErrorType.INVALID_REQUEST_ERROR);
        assertThat(error.code()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(error.message()).contains("text/plain");
        assertThat(error.param()).isNull();
    }

    @Test
    void clientErrorWithoutSpringDetailGetsGenericMessage() {
        ResponseEntity<Object> response = handle(new ErrorResponseException(HttpStatus.CONFLICT), HttpStatus.CONFLICT);

        assertThat(errorOf(response).code()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(errorOf(response).message()).isEqualTo("The request is invalid.");
    }

    @Test
    void clientErrorThatIsNotASpringErrorResponseGetsGenericMessage() {
        ResponseEntity<Object> response = handle(new IllegalArgumentException("internal"), HttpStatus.BAD_REQUEST);

        assertThat(errorOf(response).message()).isEqualTo("The request is invalid.");
    }

    @Test
    void springServerErrorBecomesInternalErrorWithoutLeakingDetails() {
        ResponseEntity<Object> response = handle(new AsyncRequestTimeoutException(), HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ErrorDetail error = errorOf(response);
        assertThat(error.type()).isEqualTo(ErrorType.API_ERROR);
        assertThat(error.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(error.message()).isEqualTo("An unexpected error occurred.");
    }

    @Test
    void keepsHeadersSetBySpring() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "30");

        ResponseEntity<Object> response = handler.handleExceptionInternal(
                new AsyncRequestTimeoutException(), null, headers, HttpStatus.SERVICE_UNAVAILABLE, request);

        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
    }

    private ResponseEntity<Object> handle(Exception ex, HttpStatus status) {
        return handler.handleExceptionInternal(ex, null, new HttpHeaders(), status, request);
    }

    private static ErrorDetail errorOf(ResponseEntity<Object> response) {
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        return ((ApiErrorResponse) response.getBody()).error();
    }
}
