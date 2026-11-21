package com.eatclub.deals.controller;

import com.eatclub.deals.TestFeeds;
import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.exception.FeedUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantFeedClient client;

    @BeforeEach
    void setUp() {
        when(client.fetch()).thenReturn(TestFeeds.challengeData());
    }

    @Test
    void returnsDealsInContractShape() throws Exception {
        mockMvc.perform(get("/api/deals").param("timeOfDay", "3:00pm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deals", hasSize(5)))
                .andExpect(jsonPath("$.deals[0].restaurantObjectId").value("DEA567C5-F64C-3C03-FF00-E3B24909BE00"))
                .andExpect(jsonPath("$.deals[0].restaurantName").value("Masala Kitchen"))
                .andExpect(jsonPath("$.deals[0].restaurantAddress1").value("55 Walsh Street"))
                .andExpect(jsonPath("$.deals[0].restarantSuburb").value("Lower East"))
                .andExpect(jsonPath("$.deals[0].restaurantOpen").value("3:00pm"))
                .andExpect(jsonPath("$.deals[0].restaurantClose").value("9:00pm"))
                .andExpect(jsonPath("$.deals[0].dealObjectId").value("DEA567C5-0000-3C03-FF00-E3B24909BE00"))
                .andExpect(jsonPath("$.deals[0].discount").value("50"))
                .andExpect(jsonPath("$.deals[0].dineIn").value("false"))
                .andExpect(jsonPath("$.deals[0].lightning").value("true"))
                .andExpect(jsonPath("$.deals[0].qtyLeft").value("5"));
    }

    /**
     * Strict comparison of the whole 3pm response with {@code expected/deals-at-3pm.json}: fails on any missing or
     * extra field, any changed value or type, and any change in the order of the deals.
     */
    @Test
    void responseMatchesTheContractExactly() throws Exception {
        String expected = new ClassPathResource("expected/deals-at-3pm.json").getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/deals").param("timeOfDay", "3:00pm"))
                .andExpect(status().isOk())
                .andExpect(content().json(expected, JsonCompareMode.STRICT));
    }

    /** Every supported format, with the number of deals the challenge feed has active at that time. */
    @ParameterizedTest(name = "timeOfDay=''{0}'' returns {1} deals")
    @CsvSource({
            // 12-hour: h:mm + am/pm with no space, upper or lower case
            "3:00pm, 5",
            "3:00PM, 5",
            "3:00Pm, 5",        // mixed case
            "6:00pm, 9",
            "6:00PM, 9",
            "9:00pm, 4",
            "9:00PM, 4",
            "10:30am, 2",       // only OzzyThai (8am–3pm)
            "10:30AM, 2",
            "12:00pm, 4",       // noon: ABC Chicken and OzzyThai
            "12:00am, 0",       // midnight
            "11:59pm, 0",
            // 24-hour: h:mm or hh:mm, no suffix
            "15:00, 5",
            "18:00, 9",
            "21:00, 4",
            "10:30, 2",
            "09:05, 2",         // leading zero
            "9:05, 2",
            "12:00, 4",
            "00:00, 0",
            "23:59, 0",
            // leading and trailing spaces are trimmed
            "'  3:00pm  ', 5",
            "'  15:00  ', 5"
    })
    void acceptsEveryValidTimeOfDayFormat(String timeOfDay, int expectedDeals) throws Exception {
        mockMvc.perform(get("/api/deals").param("timeOfDay", timeOfDay))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deals", hasSize(expectedDeals)));
    }

    @ParameterizedTest(name = "timeOfDay=''{0}'' is rejected")
    @ValueSource(strings = {
            "",           // empty
            " ",          // blank
            "abc",        // not a time
            "noon",
            "3pm",        // 12-hour time needs minutes
            "3PM",
            "12pm",
            "3:00 pm",    // no space allowed before am/pm
            "3:00 PM",
            "3:00p.m.",   // only "am"/"pm", no dots
            "3:00 p.m.",
            "3:00p",      // "a"/"p" alone isn't enough
            "3:00m",
            "3:00pmm",
            "3.00pm",     // separator must be ':'
            "3-00pm",
            "15.00",
            "3 :00pm",    // no spaces around ':'
            "3: 00pm",
            "3",          // no minutes
            "15",
            "3:0pm",      // minutes need two digits
            "3:00:00",    // seconds aren't supported
            "13:00pm",    // 12-hour clock only goes up to 12
            "0:00am",     // 12-hour clock starts at 1
            "24:00",      // 24-hour clock only goes up to 23
            "25:00",
            "10:60",      // minute out of range
            "-1:00"
    })
    void rejectsEveryInvalidTimeOfDay(String timeOfDay) throws Exception {
        mockMvc.perform(get("/api/deals").param("timeOfDay", timeOfDay))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_time_of_day"))
                .andExpect(jsonPath("$.error.param").value("timeOfDay"));
    }

    @Test
    void returnsEmptyListWhenNothingIsActive() throws Exception {
        mockMvc.perform(get("/api/deals").param("timeOfDay", "4:00am"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deals", hasSize(0)));
    }

    @Test
    void invalidTimeReturnsError() throws Exception {
        mockMvc.perform(get("/api/deals").param("timeOfDay", "25:00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_time_of_day"))
                .andExpect(jsonPath("$.error.message", containsString("Invalid time of day: '25:00'")))
                .andExpect(jsonPath("$.error.param").value("timeOfDay"))
                .andExpect(jsonPath("$.*", hasSize(1)))       // only "error" at the top level
                .andExpect(jsonPath("$.error.*", hasSize(4))); // type, code, message, param
    }

    @Test
    void missingTimeReturnsError() throws Exception {
        mockMvc.perform(get("/api/deals"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("missing_parameter"))
                .andExpect(jsonPath("$.error.message").value("Required parameter 'timeOfDay' is not present."))
                .andExpect(jsonPath("$.error.param").value("timeOfDay"));
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

    @Test
    void wrongMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/deals"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("method_not_allowed"));
    }

    @Test
    void returnsBadGatewayWhenSourceIsDown() throws Exception {
        when(client.fetch()).thenThrow(new FeedUnavailableException(new ResourceAccessException("timeout")));

        mockMvc.perform(get("/api/deals").param("timeOfDay", "3:00pm"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("api_error"))
                .andExpect(jsonPath("$.error.code").value("feed_unavailable"))
                .andExpect(jsonPath("$.error.message").value("Deal data source is currently unavailable."))
                .andExpect(jsonPath("$.error.param").doesNotExist());
    }

    @Test
    void unexpectedErrorReturnsGenericServerError() throws Exception {
        when(client.fetch()).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/api/deals").param("timeOfDay", "3:00pm"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("api_error"))
                .andExpect(jsonPath("$.error.code").value("internal_error"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }
}
