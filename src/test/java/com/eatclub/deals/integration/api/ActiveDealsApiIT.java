package com.eatclub.deals.integration.api;

import com.eatclub.deals.TestFeeds;
import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.security.BearerTokenMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/deals} through the whole app (security, service, JSON), with only the upstream feed mocked:
 * the exact response at 3pm, 6pm and 9pm, and every accepted and rejected {@code timeOfDay}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(BearerTokenMockMvc.class) // every request sends a valid access token
class ActiveDealsApiIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantFeedClient client;

    @BeforeEach
    void setUp() {
        when(client.fetch()).thenReturn(TestFeeds.challengeData());
    }

    /**
     * Strict comparison of the whole response at each challenge time with {@code expected/deals-at-<time>.json}:
     * fails on any missing or extra field, any changed value or type, and any change in the order of the deals.
     */
    @ParameterizedTest(name = "timeOfDay={0} matches {1}")
    @CsvSource({
            "3:00pm, deals-at-3pm.json",
            "6:00pm, deals-at-6pm.json",
            "9:00pm, deals-at-9pm.json"
    })
    void responseMatchesTheContractExactly(String timeOfDay, String expectedFile) throws Exception {
        String expected = new ClassPathResource("expected/" + expectedFile).getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/v1/deals").param("timeOfDay", timeOfDay))
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
            "8:59pm, 9",        // last minute before Masala Kitchen, Vrindavan and Kekou's deals end at 9pm
            "9:00pm, 4",
            "9:00PM, 4",
            "10:30am, 2",       // only OzzyThai (8am–3pm)
            "10:30AM, 2",
            "11:00pm, 0",       // ABC Chicken and Kekou close at 11pm (closing time is exclusive)
            "12:00pm, 4",       // noon: ABC Chicken and OzzyThai
            "12:00am, 0",       // midnight
            "11:59pm, 0",
            // 24-hour: h:mm or hh:mm, no suffix
            "15:00, 5",
            "18:00, 9",
            "21:00, 4",
            "10:30, 2",
            "11:00, 2",         // 11am: still only OzzyThai; ABC Chicken opens at 12pm
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
        mockMvc.perform(get("/api/v1/deals").param("timeOfDay", timeOfDay))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deals", hasSize(expectedDeals)));
    }

    @Test
    void returnsEmptyListWhenNothingIsActive() throws Exception {
        mockMvc.perform(get("/api/v1/deals").param("timeOfDay", "4:00am"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deals", hasSize(0)));
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
        mockMvc.perform(get("/api/v1/deals").param("timeOfDay", timeOfDay))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_time_of_day"))
                .andExpect(jsonPath("$.error.param").value("timeOfDay"));
    }

    @Test
    void invalidTimeReturnsError() throws Exception {
        mockMvc.perform(get("/api/v1/deals").param("timeOfDay", "25:00"))
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
        mockMvc.perform(get("/api/v1/deals"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("missing_parameter"))
                .andExpect(jsonPath("$.error.message").value("Required parameter 'timeOfDay' is not present."))
                .andExpect(jsonPath("$.error.param").value("timeOfDay"));
    }
}
