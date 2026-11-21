package com.eatclub.deals.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void givesEachRequestAnIdForTheLogsAndTheResponse() throws Exception {
        AtomicReference<String> idSeenWhileHandling = new AtomicReference<>();

        filter.doFilter(get("/api/v1/deals/peak-time"), response,
                (req, res) -> idSeenWhileHandling.set(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY)));

        String id = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(id).matches(UUID_PATTERN);
        assertThat(idSeenWhileHandling).hasValue(id);
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY)).as("cleared after the request").isNull();
    }

    @Test
    void reusesASafeIdSentByTheCaller() throws Exception {
        MockHttpServletRequest request = get("/api/v1/deals/peak-time");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "order-123_abc");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("order-123_abc");
    }

    @ParameterizedTest(name = "''{0}''")
    @ValueSource(strings = {"", "has spaces", "<script>", "line\nbreak",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}) // 65 characters
    void replacesAnUnsafeIdSentByTheCaller(String unsafeId) throws Exception {
        MockHttpServletRequest request = get("/api/v1/deals/peak-time");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, unsafeId);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void logsOneSummaryLinePerRequestWithoutHeaders(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = get("/api/v1/deals");
        request.setQueryString("timeOfDay=6:00pm");
        request.addHeader("Authorization", "Bearer secret-token-value");
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output).contains("GET /api/v1/deals?timeOfDay=6:00pm -> 200 in ");
        assertThat(output).doesNotContain("secret-token-value");
    }

    @Test
    void stillLogsAndClearsTheIdWhenHandlingFails(CapturedOutput output) {
        assertThatThrownBy(() -> filter.doFilter(get("/api/v1/deals/peak-time"), response,
                (req, res) -> { throw new ServletException("boom"); }))
                .isInstanceOf(ServletException.class);

        assertThat(output).contains("GET /api/v1/deals/peak-time -> ");
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/swagger-ui/index.html", "/v3/api-docs"})
    void skipsSwaggerUiAndTheApiDocs(String path) throws Exception {
        filter.doFilter(get(path), response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNull();
    }

    private static MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}
