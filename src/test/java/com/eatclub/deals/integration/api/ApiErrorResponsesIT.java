package com.eatclub.deals.integration.api;

import com.eatclub.deals.TestFeeds;
import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.exception.FeedUnavailableException;
import com.eatclub.deals.security.BearerTokenMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Error responses shared by the deal endpoints, through the whole app: unknown URLs, wrong methods, the upstream
 * feed being down, and unexpected failures. Each failure case is checked on both endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(BearerTokenMockMvc.class) // every request sends a valid access token
class ApiErrorResponsesIT {

    private static final String ACTIVE_DEALS = "/api/v1/deals?timeOfDay=3:00pm";
    private static final String PEAK_TIME = "/api/v1/deals/peak-time";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantFeedClient client;

    @BeforeEach
    void setUp() {
        when(client.fetch()).thenReturn(TestFeeds.challengeData());
    }

    @Test
    void unknownUrlReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("not_found"))
                .andExpect(jsonPath("$.error.message").value("No endpoint found at this URL."))
                .andExpect(jsonPath("$.error.param").doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/deals", "/api/deals/peak-time"})
    void unversionedUrlsAreNotServed(String url) throws Exception {
        mockMvc.perform(get(url).param("timeOfDay", "3:00pm"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @ParameterizedTest(name = "POST {0}")
    @ValueSource(strings = {ACTIVE_DEALS, PEAK_TIME})
    void wrongMethodReturnsMethodNotAllowed(String url) throws Exception {
        mockMvc.perform(post(url))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("method_not_allowed"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {ACTIVE_DEALS, PEAK_TIME})
    void returnsBadGatewayWhenFeedIsDown(String url) throws Exception {
        when(client.fetch()).thenThrow(new FeedUnavailableException(new ResourceAccessException("timeout")));

        mockMvc.perform(get(url))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("api_error"))
                .andExpect(jsonPath("$.error.code").value("feed_unavailable"))
                .andExpect(jsonPath("$.error.message").value("Deal data source is currently unavailable."))
                .andExpect(jsonPath("$.error.param").doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {ACTIVE_DEALS, PEAK_TIME})
    void unexpectedErrorReturnsGenericServerError(String url) throws Exception {
        when(client.fetch()).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get(url))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("api_error"))
                .andExpect(jsonPath("$.error.code").value("internal_error"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }
}
