package com.eatclub.deals.service;

import com.eatclub.deals.TestFeeds;
import com.eatclub.deals.client.RestaurantFeedClient;
import com.eatclub.deals.client.model.Deal;
import com.eatclub.deals.client.model.Restaurant;
import com.eatclub.deals.client.model.RestaurantFeed;
import com.eatclub.deals.dto.ActiveDeal;
import com.eatclub.deals.time.TimeWindow;
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
     * A deal must have both {@code open} and {@code close}, or neither. Until the product owner confirms the rule,
     * a deal with only one of them (or a blank one) ignores them and runs for its restaurant's hours.
     */
    @Nested
    class PartialDealHours {

        @ParameterizedTest(name = "deal open=''{0}'' close=''{1}''")
        @CsvSource(value = {
                "6:00pm, NULL",  // open only
                "NULL, 6:00pm",  // close only
                "6:00pm, ' '",   // blank close
                "' ', 6:00pm"    // blank open
        }, nullValues = "NULL")
        void partialHoursFallBackToTheRestaurantsHours(String open, String close) {
            // The partial 6pm value is ignored, so the deal runs for the restaurant's 9am–5pm.
            useFeed(restaurant("9:00am", "5:00pm", deal(open, close)));

            assertInactiveAt("08:59", "17:00", "18:30");
            assertActiveAt("09:00", "12:00", "16:59");
        }

        @Test
        void partialHoursAreIgnoredEvenIfUnreadable() {
            // The partial value isn't used, so it isn't parsed either; the deal still runs for the restaurant's hours.
            useFeed(restaurant("9:00am", "5:00pm", deal("abc", null)));

            assertActiveAt("12:00");
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

    /** Task 2: the window during which the most deals are active. */
    @Nested
    class PeakTime {

        @Test
        void challengeDataPeaksFrom6pmTo9pm() {
            // 9 deals from 6pm (Vrindavan opens) until 9pm (Masala Kitchen, Vrindavan and Kekou's deals end).
            useChallengeData();

            assertThat(service.findPeakTime()).contains(window("18:00", "21:00"));
        }

        @Test
        void singleDealPeaksForItsWholeWindow() {
            useFeed(restaurant("9:00am", "5:00pm", deal(null, null)));

            assertThat(service.findPeakTime()).contains(window("09:00", "17:00"));
        }

        @Test
        void peakIsWhereDealsOverlap() {
            useFeed(restaurant("9:00am", "10:00pm", deal("a", "9:00am", "5:00pm"), deal("b", "12:00pm", "8:00pm")));

            assertThat(service.findPeakTime()).contains(window("12:00", "17:00"));
        }

        @Test
        void tieReturnsTheWindowStartingEarliestInTheDay() {
            useFeed(
                    restaurant("r1", "2:00pm", "4:00pm", deal("d1", null, null)),
                    restaurant("r2", "9:00am", "11:00am", deal("d2", null, null)));

            assertThat(service.findPeakTime()).contains(window("09:00", "11:00"));
        }

        @Test
        void backToBackWindowsWithTheSameCountAreMerged() {
            // One deal ends at 12pm exactly when the next starts, so one deal is active all the way from 9am to 3pm.
            useFeed(restaurant("9:00am", "3:00pm", deal("a", "9:00am", "12:00pm"), deal("b", "12:00pm", "3:00pm")));

            assertThat(service.findPeakTime()).contains(window("09:00", "15:00"));
        }

        @Test
        void peakCanCrossMidnight() {
            // The all-day restaurant adds a boundary at midnight; the peak still runs through it as one window.
            useFeed(
                    restaurant("late", "6:00pm", "2:00am", deal("a", "10:00pm", "1:00am"), deal("b", "11:00pm", "2:00am")),
                    restaurant("all-day", null, (String) null, deal("c", null, null)));

            assertThat(service.findPeakTime()).contains(window("23:00", "01:00"));
        }

        @Test
        void dealHoursAreCutToTheRestaurantsHours() {
            // Like Vrindavan: the 3pm–9pm deal only counts from 6pm, when its restaurant opens.
            useFeed(
                    restaurant("r1", "6:00pm", "9:00pm", deal("d1", "3:00pm", "9:00pm")),
                    restaurant("r2", "4:00pm", "7:00pm", deal("d2", null, null)));

            assertThat(service.findPeakTime()).contains(window("18:00", "19:00"));
        }

        @Test
        void soldOutDealsAreNotCounted() {
            useFeed(
                    restaurant("r1", "9:00am", "11:00am", dealWithQtyLeft("sold-1", "0"), dealWithQtyLeft("sold-2", "0")),
                    restaurant("r2", "2:00pm", "4:00pm", dealWithQtyLeft("available", "1")));

            assertThat(service.findPeakTime()).contains(window("14:00", "16:00"));
        }

        /**
         * Deals in a restaurant that is open all day, so only the deals' own hours matter.
         * Deals are written as "open-close", separated by ';'.
         */
        @ParameterizedTest(name = "deals [{0}] peak {1}–{2}")
        @CsvSource({
                "'9:00am-9:00pm; 2:00pm-3:00pm',                     14:00, 15:00", // one deal inside another
                "'9:00am-5:01pm; 5:00pm-9:00pm',                     17:00, 17:01", // a one-minute peak
                "'10:00am-11:00am; 10:00am-3:00pm; 2:00pm-3:00pm',   10:00, 11:00", // drops, then back to the same peak
                "'9:00am-11:00am; 11:00pm-1:00am',                   09:00, 11:00", // tie with a window crossing midnight
                "'6:00pm-12:00am',                                   18:00, 00:00", // ends exactly at midnight
                "'12:00am-2:00am',                                   00:00, 02:00", // starts exactly at midnight
                "'12:00am-12:00pm; 12:00pm-12:00am',                 00:00, 00:00"  // back to back all day = all-day peak
        })
        void peakForDealHours(String deals, String start, String end) {
            useFeed(restaurant(null, null, deals(deals)));

            assertThat(service.findPeakTime()).contains(window(start, end));
        }

        @ParameterizedTest(name = "restaurant open=''{0}'' close=''{1}''")
        @CsvSource(value = {"NULL, NULL", "3:00pm, 3:00pm"}, nullValues = "NULL")
        void restaurantOpenAllDayIsAnAllDayPeak(String open, String close) {
            useFeed(restaurant(open, close, deal(null, null)));

            assertThat(service.findPeakTime()).contains(TimeWindow.ALL_DAY);
        }

        @Test
        void noDealsMeansNoPeak() {
            useFeed();

            assertThat(service.findPeakTime()).isEmpty();
        }

        @Test
        void dealsThatAreNeverActiveMeanNoPeak() {
            useFeed(restaurant("9:00am", "5:00pm", deal("6:00pm", "8:00pm")));

            assertThat(service.findPeakTime()).isEmpty();
        }

        @Test
        void allDealsSoldOutMeansNoPeak() {
            useFeed(restaurant("9:00am", "5:00pm", dealWithQtyLeft("a", "0"), dealWithQtyLeft("b", "0")));

            assertThat(service.findPeakTime()).isEmpty();
        }

        @ParameterizedTest(name = "qtyLeft=''{0}''")
        @CsvSource(value = {"NULL", "abc"}, nullValues = "NULL")
        void dealWithUnknownQuantityIsCounted(String qtyLeft) {
            useFeed(restaurant("9:00am", "11:00am", dealWithQtyLeft("d", qtyLeft)));

            assertThat(service.findPeakTime()).contains(window("09:00", "11:00"));
        }

        @Test
        void dealWithPartialHoursIsCountedForTheRestaurantsHours() {
            useFeed(restaurant("9:00am", "5:00pm", deal("6:00pm", null)));

            assertThat(service.findPeakTime()).contains(window("09:00", "17:00"));
        }

        @Test
        void dealWithMalformedHoursIsNotCounted() {
            // If the 9am–5pm deal were counted, it would tie with 6pm–8pm and win as the earlier window.
            useFeed(
                    restaurant("r1", "9:00am", "5:00pm", deal("bad", "not-a-time", "5:00pm")),
                    restaurant("r2", "6:00pm", "8:00pm", deal("good", null, null)));

            assertThat(service.findPeakTime()).contains(window("18:00", "20:00"));
        }

        @Test
        void dealsSharingAnIdAreCountedSeparately() {
            // Like Kekou and Gyoza Gyoza: if the shared ID were counted once, the peak would be 9am–5pm.
            useFeed(
                    restaurant("r1", "9:00am", "5:00pm", deal("same-id", null, null)),
                    restaurant("r2", "2:00pm", "4:00pm", deal("same-id", null, null)));

            assertThat(service.findPeakTime()).contains(window("14:00", "16:00"));
        }

        @Test
        void peakAgreesWithActiveDeals() {
            // The end is exclusive: the peak count holds at the start and the minute before the end, but not at the end.
            useChallengeData();

            assertThat(activeAt(18, 0)).hasSize(9);
            assertThat(activeAt(20, 59)).hasSize(9);
            assertThat(activeAt(21, 0)).hasSizeLessThan(9);
            assertThat(activeAt(17, 59)).hasSizeLessThan(9);
        }

        private static TimeWindow window(String start, String end) {
            return new TimeWindow(LocalTime.parse(start), LocalTime.parse(end));
        }

        /** "9:00am-5:00pm; 2:00pm-3:00pm" → two deals with those hours. */
        private static Deal[] deals(String hours) {
            String[] parts = hours.split(";");
            Deal[] deals = new Deal[parts.length];
            for (int i = 0; i < parts.length; i++) {
                String[] openClose = parts[i].trim().split("-");
                deals[i] = deal("d" + i, openClose[0], openClose[1]);
            }
            return deals;
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
