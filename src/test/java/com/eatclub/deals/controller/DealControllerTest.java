package com.eatclub.deals.controller;

import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.client.model.RestaurantFeed;
import com.eatclub.deals.exception.FeedUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

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

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private RestaurantFeedClient client;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/challengedata.json")) {
            when(client.fetch()).thenReturn(jsonMapper.readValue(in, RestaurantFeed.class));
        }
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

    @Test
    void acceptsTwentyFourHourTime() throws Exception {
        mockMvc.perform(get("/api/deals").param("timeOfDay", "18:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deals", hasSize(9)));
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
