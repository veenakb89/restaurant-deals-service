package com.eatclub.deals.service;

import com.eatclub.deals.TestFeeds;
import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.client.model.Deal;
import com.eatclub.deals.client.model.Restaurant;
import com.eatclub.deals.client.model.RestaurantFeed;
import com.eatclub.deals.dto.ActiveDeal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DealServiceTest {

    private final RestaurantFeedClient client = mock(RestaurantFeedClient.class);
    private final DealService service = new DealService(client);

    /** The three times the challenge asks about, against the saved challenge feed. */
    @Nested
    class ChallengeTestCases {

        @Test
        void returnsFiveDealsAt3pm() {
            useChallengeData();

            assertThat(dealIdsAt(15, 0)).containsExactly(
                    "DEA567C5-0000-3C03-FF00-E3B24909BE00", // Masala Kitchen opens at 3pm (start is inclusive)
                    "DEA567C5-1111-3C03-FF00-E3B24909BE00",
                    "D80263E8-0000-2C70-FF6B-D854ADB8DB00", // ABC Chicken
                    "D80263E8-1111-2C70-FF6B-D854ADB8DB00",
                    "B5713CD0-0000-40C7-AFC3-7D46D26B00BF"  // Kekou's 2pm–9pm deal; its 5pm deal hasn't started
            );
            // Excluded: OzzyThai closes at 3pm (end is exclusive); Vrindavan's deal starts 3pm but the restaurant opens at 6pm.
        }

        @Test
        void returnsNineDealsAt6pm() {
            useChallengeData();

            assertThat(dealIdsAt(18, 0)).containsExactly(
                    "DEA567C5-0000-3C03-FF00-E3B24909BE00", // Masala Kitchen
                    "DEA567C5-1111-3C03-FF00-E3B24909BE00",
                    "D80263E8-0000-2C70-FF6B-D854ADB8DB00", // ABC Chicken
                    "D80263E8-1111-2C70-FF6B-D854ADB8DB00",
                    "CDB2B42A-0000-EE20-FF45-8D0A8057E200", // Vrindavan: restaurant opens at 6pm (inclusive)
                    "B5713CD0-0000-40C7-AFC3-7D46D26B00BF", // Kekou, both deals
                    "B5713CD0-1111-40C7-AFC3-7D46D26B00BF",
                    "B5913CD0-0000-40C7-AFC3-7D46D26B01BF", // Gyoza Gyoza
                    "B5713CD0-1111-40C7-AFC3-7D46D26B00BF"  // same deal ID as Kekou's second deal (data quirk)
            );
            // Excluded: OzzyThai closed at 3pm.
        }

        @Test
        void returnsFourDealsAt9pm() {
            useChallengeData();

            assertThat(dealIdsAt(21, 0)).containsExactly(
                    "D80263E8-0000-2C70-FF6B-D854ADB8DB00", // ABC Chicken (open until 11pm)
                    "D80263E8-1111-2C70-FF6B-D854ADB8DB00",
                    "B5913CD0-0000-40C7-AFC3-7D46D26B01BF", // Gyoza Gyoza (open until 10pm)
                    "B5713CD0-1111-40C7-AFC3-7D46D26B00BF"
            );
            // Excluded: Masala Kitchen, Vrindavan and both Kekou deals end at 9pm (end is exclusive).
        }

        @Test
        void returnsNothingWhenEverythingIsClosed() {
            useChallengeData();

            assertThat(dealIdsAt(4, 0)).isEmpty();
        }
    }

    /** Which hours apply to a deal: its own, or its restaurant's. */
    @Nested
    class DealHours {

        @Test
        void dealWithoutHoursRunsForTheRestaurantsHours() {
            useFeed(restaurant("9:00am", "5:00pm", deal(null, null)));

            assertThat(activeAt(8, 59)).isEmpty();
            assertThat(activeAt(9, 0)).hasSize(1);
            assertThat(activeAt(16, 59)).hasSize(1);
            assertThat(activeAt(17, 0)).isEmpty();
        }

        @Test
        void dealWithOwnHoursInsideRestaurantHoursUsesItsOwnHours() {
            useFeed(restaurant("9:00am", "10:00pm", deal("5:00pm", "7:00pm")));

            assertThat(activeAt(16, 59)).isEmpty();
            assertThat(activeAt(17, 0)).hasSize(1);
            assertThat(activeAt(19, 0)).isEmpty();
        }

        @Test
        void startAndEndAreAcceptedAsDealHours() {
            // Some deals in the feed use "start"/"end" instead of "open"/"close".
            useFeed(TestFeeds.fromJson("""
                    {"restaurants": [{"objectId": "r", "open": "9:00am", "close": "10:00pm",
                      "deals": [{"objectId": "d", "start": "5:00pm", "end": "7:00pm"}]}]}"""));

            assertThat(activeAt(12, 0)).isEmpty();
            assertThat(activeAt(18, 0)).hasSize(1);
        }
    }

    /**
     * A deal with only {@code open} or only {@code close}: the missing side comes from the restaurant.
     * Partial times are read on the day the restaurant opens.
     */
    @Nested
    class PartialDealHours {

        // --- restaurant open 9am–10pm ---

        @Test
        void openOnlyRunsUntilTheRestaurantCloses() {
            useFeed(restaurant("9:00am", "10:00pm", deal("6:00pm", null)));

            assertInactiveAt("17:59", "22:00");
            assertActiveAt("18:00", "21:59");
        }

        @Test
        void closeOnlyRunsFromWhenTheRestaurantOpens() {
            useFeed(restaurant("9:00am", "10:00pm", deal(null, "2:00pm")));

            assertInactiveAt("08:59", "14:00");
            assertActiveAt("09:00", "13:59");
        }

        @Test
        void openBeforeTheRestaurantOpensStartsWhenItOpens() {
            useFeed(restaurant("9:00am", "10:00pm", deal("7:00am", null)));

            assertInactiveAt("07:00", "08:59", "22:00");
            assertActiveAt("09:00", "21:59");
        }

        @Test
        void closeAfterTheRestaurantClosesEndsWhenItCloses() {
            useFeed(restaurant("9:00am", "10:00pm", deal(null, "11:00pm")));

            assertActiveAt("09:00", "21:59");
            assertInactiveAt("22:00", "22:30");
        }

        @ParameterizedTest(name = "open={0}")
        @ValueSource(strings = {"10:00pm", "11:00pm"})
        void openAtOrAfterClosingIsNeverActive(String open) {
            useFeed(restaurant("9:00am", "10:00pm", deal(open, null)));

            assertInactiveAt("09:00", "12:00", "21:59", "22:30", "23:30");
        }

        @ParameterizedTest(name = "close={0}")
        @ValueSource(strings = {"9:00am", "8:00am"})
        void closeAtOrBeforeOpeningIsNeverActive(String close) {
            useFeed(restaurant("9:00am", "10:00pm", deal(null, close)));

            assertInactiveAt("08:30", "09:00", "12:00", "21:59");
        }

        @ParameterizedTest(name = "open=''{0}'' close=''{1}''")
        @CsvSource({
                "' ', 2:00pm, 13:59, 14:00",   // blank open → restaurant's 9am
                "6:00pm, ' ', 18:00, 17:59"    // blank close → restaurant's 10pm
        })
        void blankValueCountsAsMissing(String open, String close, String activeTime, String inactiveTime) {
            useFeed(restaurant("9:00am", "10:00pm", deal(open, close)));

            assertActiveAt(activeTime);
            assertInactiveAt(inactiveTime);
        }

        // --- restaurant open 6pm–2am (crosses midnight) ---

        @Test
        void openBeforeMidnightRunsUntilClosingAfterMidnight() {
            useFeed(restaurant("6:00pm", "2:00am", deal("11:00pm", null)));

            assertInactiveAt("22:59", "02:00");
            assertActiveAt("23:00", "00:30", "01:59");
        }

        @Test
        void openAfterMidnightWhileStillOpenRunsUntilClosing() {
            useFeed(restaurant("6:00pm", "2:00am", deal("1:00am", null)));

            assertInactiveAt("18:00", "23:00", "00:59", "02:00");
            assertActiveAt("01:00", "01:59");
        }

        @Test
        void closeAfterMidnightWhileStillOpen() {
            useFeed(restaurant("6:00pm", "2:00am", deal(null, "1:00am")));

            assertActiveAt("18:00", "00:59");
            assertInactiveAt("01:00", "17:59");
        }

        @Test
        void openInTheClosedGapCountsAsBeforeOpening() {
            // 3am is while the restaurant is closed, so it's read as earlier on the opening day: the deal starts at 6pm.
            useFeed(restaurant("6:00pm", "2:00am", deal("3:00am", null)));

            assertActiveAt("18:00", "01:59");
            assertInactiveAt("03:00", "17:59", "02:00");
        }

        @Test
        void closeInTheClosedGapCountsAsBeforeOpening() {
            // 3am is read as earlier on the opening day, before the 6pm opening, so the deal is never active.
            useFeed(restaurant("6:00pm", "2:00am", deal(null, "3:00am")));

            assertInactiveAt("18:00", "01:00", "03:00");
        }

        // --- restaurant with no hours (open all day) ---

        @Test
        void openOnlyAtAnAllDayRestaurantRunsUntilMidnight() {
            useFeed(restaurant(null, null, deal("6:00pm", null)));

            assertInactiveAt("17:59", "00:00");
            assertActiveAt("18:00", "23:59");
        }

        @Test
        void closeOnlyAtAnAllDayRestaurantRunsFromMidnight() {
            useFeed(restaurant(null, null, deal(null, "2:00pm")));

            assertActiveAt("00:00", "13:59");
            assertInactiveAt("14:00", "23:59");
        }

        @Test
        void closeAtMidnightAtAnAllDayRestaurantIsAllDay() {
            useFeed(restaurant(null, null, deal(null, "12:00am")));

            assertActiveAt("00:00", "12:00", "23:59");
        }
    }

    /** A deal is only available while its restaurant is open. */
    @Nested
    class RestaurantMustBeOpen {

        @Test
        void dealStartingBeforeOpeningBeginsWhenTheRestaurantOpens() {
            // Like Vrindavan: deal 3pm–9pm, restaurant 6pm–9pm.
            useFeed(restaurant("6:00pm", "9:00pm", deal("3:00pm", "9:00pm")));

            assertThat(activeAt(15, 0)).isEmpty();
            assertThat(activeAt(18, 0)).hasSize(1);
        }

        @Test
        void dealEndingAfterClosingStopsWhenTheRestaurantCloses() {
            useFeed(restaurant("9:00am", "10:00pm", deal("5:00pm", "11:00pm")));

            assertThat(activeAt(21, 59)).hasSize(1);
            assertThat(activeAt(22, 0)).isEmpty();
            assertThat(activeAt(22, 30)).isEmpty();
        }

        @Test
        void dealEntirelyOutsideRestaurantHoursIsNeverActive() {
            useFeed(restaurant("9:00am", "5:00pm", deal("6:00pm", "8:00pm")));

            assertThat(activeAt(12, 0)).isEmpty();
            assertThat(activeAt(19, 0)).isEmpty();
        }

        @Test
        void restaurantWithoutHoursIsTreatedAsOpenAllDay() {
            useFeed(restaurant(null, null, deal(null, null)));

            assertThat(activeAt(4, 0)).hasSize(1);
            assertThat(activeAt(23, 30)).hasSize(1);
        }

        @Test
        void restaurantWithOnlyAnOpeningTimeIsTreatedAsOpenAllDay() {
            useFeed(restaurant("9:00am", null, deal(null, null)));

            assertThat(activeAt(4, 0)).hasSize(1);
            assertThat(activeAt(23, 30)).hasSize(1);
        }

        @Test
        void restaurantWithBlankHoursIsTreatedAsOpenAllDay() {
            useFeed(restaurant(" ", "", deal(null, null)));

            assertThat(activeAt(4, 0)).hasSize(1);
        }

        @Test
        void restaurantWithoutHoursStillAppliesTheDealsOwnHours() {
            useFeed(restaurant(null, null, deal("9:00am", "5:00pm")));

            assertThat(activeAt(12, 0)).hasSize(1);
            assertThat(activeAt(18, 0)).isEmpty();
        }
    }

    /** Hours that cross midnight, e.g. a late-night venue open 6pm–2am. */
    @Nested
    class AcrossMidnight {

        @Test
        void restaurantOpenPastMidnightHasDealsAfterMidnight() {
            useFeed(restaurant("6:00pm", "2:00am", deal(null, null)));

            assertThat(activeAt(23, 0)).hasSize(1);
            assertThat(activeAt(1, 59)).hasSize(1);
            assertThat(activeAt(2, 0)).isEmpty();
            assertThat(activeAt(17, 0)).isEmpty();
        }

        @Test
        void dealAcrossMidnightInsideLateNightHours() {
            useFeed(restaurant("6:00pm", "2:00am", deal("11:00pm", "1:00am")));

            assertThat(activeAt(22, 59)).isEmpty();
            assertThat(activeAt(0, 30)).hasSize(1);
            assertThat(activeAt(1, 0)).isEmpty();
        }
    }

    /** Bad or missing data in the feed must not break the whole response. */
    @Nested
    class MalformedData {

        @Test
        void skipsDealsWithMalformedHours() {
            useFeed(restaurant("9:00am", "5:00pm", deal("not-a-time", "5:00pm"), deal(null, null)));

            assertThat(activeAt(12, 0)).hasSize(1);
        }

        @Test
        void skipsDealsWithAMalformedPartialHour() {
            useFeed(restaurant("9:00am", "5:00pm", deal("abc", null), deal(null, "xyz"), deal("ok", null, null)));

            assertThat(dealIdsAt(12, 0)).containsExactly("ok");
        }

        @Test
        void restaurantWithMalformedHoursSkipsAllItsDealsButNotOtherRestaurants() {
            useFeed(
                    restaurant("r1", "late", "5:00pm", deal("d1", null, null), deal("d2", "9:00am", "10:00am")),
                    restaurant("r2", "9:00am", "5:00pm", deal("d3", null, null)));

            assertThat(dealIdsAt(9, 30)).containsExactly("d3");
        }

        @Test
        void restaurantWithNullDealsContributesNothing() {
            useFeed(new Restaurant("r", "Test", "1 Street", "Suburb", List.of(), null, "9:00am", "5:00pm", null));

            assertThat(activeAt(12, 0)).isEmpty();
        }

        @Test
        void feedWithNullRestaurantsReturnsNoDeals() {
            when(client.fetch()).thenReturn(new RestaurantFeed(null));

            assertThat(activeAt(12, 0)).isEmpty();
        }
    }

    /** What the response contains. */
    @Nested
    class ResponseContent {

        @Test
        void mapsAllResponseFields() {
            useChallengeData();

            ActiveDeal first = service.findActiveDeals(LocalTime.of(15, 0)).getFirst();

            assertThat(first).isEqualTo(new ActiveDeal(
                    "DEA567C5-F64C-3C03-FF00-E3B24909BE00", "Masala Kitchen", "55 Walsh Street", "Lower East",
                    "3:00pm", "9:00pm", "DEA567C5-0000-3C03-FF00-E3B24909BE00", "50", "false", "true", "5"));
        }

        @Test
        void passesValuesThroughUnchanged() {
            useChallengeData();

            // OzzyThai's name has a trailing space in the source; it's returned exactly as given.
            assertThat(service.findActiveDeals(LocalTime.of(10, 0)))
                    .extracting(ActiveDeal::restaurantName)
                    .contains("OzzyThai Cafe Bar ");
        }

        @Test
        void keepsDealsThatShareAnIdAcrossRestaurants() {
            useChallengeData();

            assertThat(service.findActiveDeals(LocalTime.of(18, 0)))
                    .filteredOn(d -> d.dealObjectId().equals("B5713CD0-1111-40C7-AFC3-7D46D26B00BF"))
                    .extracting(ActiveDeal::restaurantName, ActiveDeal::dineIn)
                    .containsExactly(
                            tuple("Kekou", "true"),
                            tuple("Gyoza Gyoza Melbourne Central", "false"));
        }

        @Test
        void keepsSourceOrder() {
            useFeed(
                    restaurant("r2", "9:00am", "5:00pm", deal("b", null, null), deal("a", null, null)),
                    restaurant("r1", "9:00am", "5:00pm", deal("c", null, null)));

            assertThat(dealIdsAt(12, 0)).containsExactly("b", "a", "c");
        }
    }

    /** A deal with nothing left can't be redeemed, so it isn't active. */
    @Nested
    class SoldOutDeals {

        @Test
        void dealWithZeroQuantityLeftIsNotActive() {
            useFeed(restaurant("9:00am", "5:00pm", dealWithQtyLeft("sold-out", "0"), dealWithQtyLeft("available", "1")));

            assertThat(dealIdsAt(12, 0)).containsExactly("available");
        }

        @Test
        void dealWithNegativeQuantityLeftIsNotActive() {
            useFeed(restaurant("9:00am", "5:00pm", dealWithQtyLeft("d", "-1")));

            assertThat(activeAt(12, 0)).isEmpty();
        }

        @ParameterizedTest(name = "qtyLeft=''{0}''")
        @CsvSource(value = {"NULL", "''", "' '", "abc"}, nullValues = "NULL")
        void dealWithUnknownQuantityIsKept(String qtyLeft) {
            // The source hasn't said the deal is sold out, so it stays active.
            useFeed(restaurant("9:00am", "5:00pm", dealWithQtyLeft("d", qtyLeft)));

            assertThat(activeAt(12, 0)).hasSize(1);
        }
    }

    /** Hours whose open and close times are equal (e.g. 3pm–3pm) mean open all day. */
    @Nested
    class EqualOpenAndClose {

        @Test
        void dealOpenAndClosingAt3pmRunsForAllOfTheRestaurantsHours() {
            // The deal is all day, so the restaurant's 9am–10pm is what limits it.
            useFeed(restaurant("9:00am", "10:00pm", deal("3:00pm", "3:00pm")));

            assertThat(activeAt(8, 59)).isEmpty();
            assertThat(activeAt(9, 0)).hasSize(1);
            assertThat(activeAt(15, 1)).hasSize(1);
            assertThat(activeAt(21, 59)).hasSize(1);
            assertThat(activeAt(22, 0)).isEmpty();
        }

        @Test
        void restaurantOpenAndClosingAt3pmIsOpenAllDay() {
            useFeed(restaurant("3:00pm", "3:00pm", deal(null, null)));

            assertThat(activeAt(4, 0)).hasSize(1);
            assertThat(activeAt(15, 0)).hasSize(1);
            assertThat(activeAt(15, 1)).hasSize(1);
            assertThat(activeAt(23, 59)).hasSize(1);
        }

        @Test
        void bothOpenAllDayMeansTheDealIsAlwaysActive() {
            useFeed(restaurant("3:00pm", "3:00pm", deal("9:00am", "9:00am")));

            assertThat(activeAt(0, 0)).hasSize(1);
            assertThat(activeAt(12, 0)).hasSize(1);
        }
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private void useChallengeData() {
        when(client.fetch()).thenReturn(TestFeeds.challengeData());
    }

    private void useFeed(Restaurant... restaurants) {
        useFeed(new RestaurantFeed(List.of(restaurants)));
    }

    private void useFeed(RestaurantFeed feed) {
        when(client.fetch()).thenReturn(feed);
    }

    private List<ActiveDeal> activeAt(int hour, int minute) {
        return service.findActiveDeals(LocalTime.of(hour, minute));
    }

    private List<String> dealIdsAt(int hour, int minute) {
        return activeAt(hour, minute).stream().map(ActiveDeal::dealObjectId).toList();
    }

    /** Asserts the single deal in the feed is active at each 24-hour time, e.g. "18:00". */
    private void assertActiveAt(String... times) {
        for (String time : times) {
            assertThat(service.findActiveDeals(LocalTime.parse(time))).as("active at %s", time).hasSize(1);
        }
    }

    /** Asserts no deal is active at each 24-hour time, e.g. "17:59". */
    private void assertInactiveAt(String... times) {
        for (String time : times) {
            assertThat(service.findActiveDeals(LocalTime.parse(time))).as("inactive at %s", time).isEmpty();
        }
    }

    private static Restaurant restaurant(String open, String close, Deal... deals) {
        return restaurant("r", open, close, deals);
    }

    private static Restaurant restaurant(String id, String open, String close, Deal... deals) {
        return new Restaurant(id, "Test " + id, "1 Street", "Suburb", List.of(), null, open, close, List.of(deals));
    }

    private static Deal deal(String open, String close) {
        return deal("d", open, close);
    }

    private static Deal deal(String id, String open, String close) {
        return new Deal(id, "10", "true", "false", open, close, "1");
    }

    private static Deal dealWithQtyLeft(String id, String qtyLeft) {
        return new Deal(id, "10", "true", "false", null, null, qtyLeft);
    }
}
