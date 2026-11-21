package com.eatclub.deals.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an ID and logs one summary line when it finishes.
 * <ul>
 *   <li>The ID is the incoming {@code X-Request-Id} header if it's safe (letters, digits, {@code -} or {@code _},
 *       up to 64 characters), otherwise a new UUID. It's returned in the {@code X-Request-Id} response header and
 *       put in the logging context as {@code requestId}, so every log line written for this request shows it.</li>
 *   <li>The summary is {@code GET /api/v1/deals?timeOfDay=6:00pm -> 200 in 12 ms}. Headers are never logged, so the
 *       access token stays out of the logs.</li>
 * </ul>
 * It runs before everything else, including security, so rejected requests get an ID and a summary too.
 * Swagger UI and the API docs are skipped, to keep the logs readable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdFor(request);
        long start = System.nanoTime();
        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("{} {} -> {} in {} ms", request.getMethod(), pathAndQuery(request), response.getStatus(),
                    (System.nanoTime() - start) / 1_000_000);
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    /** The caller's ID if it's safe to log and echo back; otherwise a new one. */
    private static String requestIdFor(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }

    private static String pathAndQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }
}
