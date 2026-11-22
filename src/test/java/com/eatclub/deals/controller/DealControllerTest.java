package com.eatclub.deals.controller;

import com.eatclub.deals.dto.ActiveDeal;
import com.eatclub.deals.service.DealService;
import com.eatclub.deals.time.TimeWindow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The controller on its own, with {@link DealService} mocked: it parses {@code timeOfDay}, calls the service and
 * maps the result. Security is left out (see {@code ApiAuthorizationIT}). The full API, with real data, is tested
 * in {@code com.eatclub.deals.integration.api}.
 */
@WebMvcTest(DealController.class)
@AutoConfigureMockMvc(addFilters = false)
class DealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DealService dealService;

    @Test
    void parsesTimeOfDayAndReturnsTheServicesDeals() throws Exception {
        ActiveDeal deal = new ActiveDeal("r1", "Masala Kitchen", "55 Walsh Street", "Lower East",
                "3:00pm", "9:00pm", "d1", "50", "false", "true", "5");
        when(dealService.findActiveDeals(LocalTime.of(15, 0))).thenReturn(List.of(deal));

        mockMvc.perform(get("/api/v1/deals").param("timeOfDay", "3:00pm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deals", hasSize(1)))
                .andExpect(jsonPath("$.deals[0].dealObjectId").value("d1"));

        verify(dealService).findActiveDeals(LocalTime.of(15, 0));
    }

    @Test
    void invalidTimeOfDayIsRejectedWithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/deals").param("timeOfDay", "25:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_time_of_day"));

        verifyNoInteractions(dealService);
    }

    @Test
    void missingTimeOfDayIsRejectedWithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/deals"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("missing_parameter"));

        verifyNoInteractions(dealService);
    }

    @Test
    void peakTimeFormatsTheServicesWindow() throws Exception {
        when(dealService.findPeakTime())
                .thenReturn(Optional.of(new TimeWindow(LocalTime.of(18, 0), LocalTime.of(21, 0))));

        mockMvc.perform(get("/api/v1/deals/peak-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakTimeStart").value("6:00pm"))
                .andExpect(jsonPath("$.peakTimeEnd").value("9:00pm"));
    }

    @Test
    void noPeakTimeReturnsNulls() throws Exception {
        when(dealService.findPeakTime()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/deals/peak-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakTimeStart").value(nullValue()))
                .andExpect(jsonPath("$.peakTimeEnd").value(nullValue()));
    }
}
