package com.eatclub.deals.integration.api;

import com.eatclub.deals.TestFeeds;
import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.security.BearerTokenMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/deals/peak-time} through the whole app, with only the upstream feed mocked: the exact
 * response for the challenge data, and the edge cases of the peak window.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(BearerTokenMockMvc.class) // every request sends a valid access token
class PeakTimeApiIT {

    private static final String PEAK_TIME = "/api/v1/deals/peak-time";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantFeedClient client;

    @BeforeEach
    void setUp() {
        when(client.fetch()).thenReturn(TestFeeds.challengeData());
    }

    /** Strict comparison of the whole response: exactly these two fields, with these values. */
    @Test
    void responseMatchesTheContractExactly() throws Exception {
        mockMvc.perform(get(PEAK_TIME))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"peakTimeStart": "6:00pm", "peakTimeEnd": "9:00pm"}""", JsonCompareMode.STRICT));
    }

    @Test
    void peakTimeIsNullWhenNoDealIsActive() throws Exception {
        when(client.fetch()).thenReturn(TestFeeds.fromJson("{\"restaurants\": []}"));

        mockMvc.perform(get(PEAK_TIME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakTimeStart").value(nullValue()))
                .andExpect(jsonPath("$.peakTimeEnd").value(nullValue()))
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    void allDayPeakIsReturnedAsMidnightToMidnight() throws Exception {
        when(client.fetch()).thenReturn(TestFeeds.fromJson("""
                {"restaurants": [{"objectId": "r", "deals": [{"objectId": "d"}]}]}"""));

        mockMvc.perform(get(PEAK_TIME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakTimeStart").value("12:00am"))
                .andExpect(jsonPath("$.peakTimeEnd").value("12:00am"));
    }

    @Test
    void peakTimeIgnoresUnexpectedParameters() throws Exception {
        mockMvc.perform(get(PEAK_TIME).param("timeOfDay", "3:00pm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakTimeStart").value("6:00pm"))
                .andExpect(jsonPath("$.peakTimeEnd").value("9:00pm"));
    }
}
