package com.eatclub.deals.service;

import com.eatclub.deals.time.TimeWindow;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Finds the time window during which the most deals are active.
 * <ul>
 *   <li>The number of active deals can only change at an opening or closing time (a boundary), so the count is
 *       taken at each boundary; it stays the same until the next one.</li>
 *   <li>Neighbouring stretches with the same highest count are merged, including across midnight.</li>
 *   <li>If several separate windows tie, the one that starts earliest in the day (from midnight) is returned.</li>
 *   <li>If the highest count lasts all day, {@link TimeWindow#ALL_DAY} is returned.</li>
 * </ul>
 */
final class PeakTimeCalculator {

    private PeakTimeCalculator() {
    }

    /** The peak window, or empty if no deal is ever active. */
    static Optional<TimeWindow> findPeak(List<DealAvailability> deals) {
        List<LocalTime> boundaries = boundaries(deals);
        int[] counts = activeCounts(deals, boundaries);
        int peak = Arrays.stream(counts).max().orElse(0);
        return peak == 0 ? Optional.empty() : Optional.of(peakWindow(boundaries, counts, peak));
    }

    /** Every time at which some deal can start or stop, sorted from midnight. */
    private static List<LocalTime> boundaries(List<DealAvailability> deals) {
        return deals.stream()
                .flatMap(DealAvailability::boundaries)
                .distinct()
                .sorted()
                .toList();
    }

    /** {@code counts[i]} is the number of deals active from {@code boundaries[i]} until the next boundary. */
    private static int[] activeCounts(List<DealAvailability> deals, List<LocalTime> boundaries) {
        return boundaries.stream()
                .mapToInt(time -> (int) deals.stream().filter(deal -> deal.isActiveAt(time)).count())
                .toArray();
    }

    /** The earliest stretch at the peak count. Boundaries are sorted, so the first one found starts earliest. */
    private static TimeWindow peakWindow(List<LocalTime> boundaries, int[] counts, int peak) {
        for (int i = 0; i < counts.length; i++) {
            if (startsAPeak(counts, i, peak)) {
                return new TimeWindow(boundaries.get(i), boundaries.get(endOfPeak(counts, i, peak)));
            }
        }
        return TimeWindow.ALL_DAY; // never drops below the peak
    }

    /** The count reaches the peak at {@code i}, coming from a lower count (wrapping around midnight). */
    private static boolean startsAPeak(int[] counts, int i, int peak) {
        int previous = (i + counts.length - 1) % counts.length;
        return counts[i] == peak && counts[previous] < peak;
    }

    /** The first boundary after {@code start} where the count drops below the peak (wrapping around midnight). */
    private static int endOfPeak(int[] counts, int start, int peak) {
        int next = (start + 1) % counts.length;
        while (counts[next] == peak) {
            next = (next + 1) % counts.length;
        }
        return next;
    }
}
