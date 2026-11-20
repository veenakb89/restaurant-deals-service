package com.eatclub.deals.controller;

import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.client.model.RestaurantFeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void rejectsInvalidTime() throws Exception {
        mockMvc.perform(get("/api/deals").param("timeOfDay", "25:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void rejectsMissingTime() throws Exception {
        mockMvc.perform(get("/api/deals"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadGatewayWhenSourceIsDown() throws Exception {
        when(client.fetch()).thenThrow(new ResourceAccessException("timeout"));

        mockMvc.perform(get("/api/deals").param("timeOfDay", "3:00pm"))
                .andExpect(status().isBadGateway());
    }
}
