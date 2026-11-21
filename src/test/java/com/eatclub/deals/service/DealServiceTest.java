package com.eatclub.deals.service;

import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.client.model.Deal;
import com.eatclub.deals.client.model.Restaurant;
import com.eatclub.deals.client.model.RestaurantFeed;
import com.eatclub.deals.dto.ActiveDeal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DealServiceTest {

    private final RestaurantFeedClient client = mock(RestaurantFeedClient.class);
    private final DealService service = new DealService(client);

    @Test
    void returnsFiveDealsAt3pm() throws IOException {
        useFixture();

        assertThat(dealIds(service.findActiveDeals(LocalTime.of(15, 0)))).containsExactly(
                "DEA567C5-0000-3C03-FF00-E3B24909BE00", // Masala Kitchen opens at 3pm (inclusive)
                "DEA567C5-1111-3C03-FF00-E3B24909BE00",
                "D80263E8-0000-2C70-FF6B-D854ADB8DB00", // ABC Chicken
                "D80263E8-1111-2C70-FF6B-D854ADB8DB00",
                "B5713CD0-0000-40C7-AFC3-7D46D26B00BF"  // Kekou 2pm-9pm deal
        );
        // Excluded: OzzyThai closes at 3pm (exclusive); Vrindavan deal starts 3pm but restaurant opens 6pm.
    }

    @Test
    void returnsNineDealsAt6pm() throws IOException {
        useFixture();

        List<ActiveDeal> deals = service.findActiveDeals(LocalTime.of(18, 0));

        assertThat(deals).hasSize(9);
        assertThat(deals).extracting(ActiveDeal::restaurantName).doesNotContain("OzzyThai Cafe Bar ");
    }

    @Test
    void returnsFourDealsAt9pm() throws IOException {
        useFixture();

        assertThat(service.findActiveDeals(LocalTime.of(21, 0)))
                .extracting(ActiveDeal::restaurantName)
                .containsExactly("ABC Chicken", "ABC Chicken",
                        "Gyoza Gyoza Melbourne Central", "Gyoza Gyoza Melbourne Central");
    }

    @Test
    void mapsAllResponseFields() throws IOException {
        useFixture();

        ActiveDeal first = service.findActiveDeals(LocalTime.of(15, 0)).getFirst();

        assertThat(first).isEqualTo(new ActiveDeal(
                "DEA567C5-F64C-3C03-FF00-E3B24909BE00", "Masala Kitchen", "55 Walsh Street", "Lower East",
                "3:00pm", "9:00pm", "DEA567C5-0000-3C03-FF00-E3B24909BE00", "50", "false", "true", "5"));
    }

    @Test
    void skipsDealsWithMalformedHours() {
        when(client.fetch()).thenReturn(new RestaurantFeed(List.of(
                restaurant("9:00am", "5:00pm", deal("not-a-time", "5:00pm"), deal(null, null)))));

        assertThat(service.findActiveDeals(LocalTime.NOON)).hasSize(1);
    }

    private void useFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/challengedata.json")) {
            when(client.fetch()).thenReturn(JsonMapper.builder().build().readValue(in, RestaurantFeed.class));
        }
    }

    private static List<String> dealIds(List<ActiveDeal> deals) {
        return deals.stream().map(ActiveDeal::dealObjectId).toList();
    }

    private static Restaurant restaurant(String open, String close, Deal... deals) {
        return new Restaurant("r", "Test", "1 Street", "Suburb", List.of(), null, open, close, List.of(deals));
    }

    private static Deal deal(String open, String close) {
        return new Deal("d", "10", "true", "false", open, close, "1");
    }
}
