package com.eatclub.deals.time;

import java.time.LocalTime;
import java.util.Optional;

/**
 * A daily time window. The start is inclusive and the end is exclusive, so a restaurant
 * open 3:00pm–9:00pm is open at 3:00pm but closed at 9:00pm.
 * <ul>
 *   <li>A window whose end is earlier than its start crosses midnight (e.g. 6:00pm–2:00am).</li>
 *   <li>A window whose start equals its end covers the whole day (e.g. 3:00pm–3:00pm).</li>
 * </ul>
 */
public record TimeWindow(LocalTime start, LocalTime end) {

    /** Open at every time of day; used when the source gives no hours. */
    public static final TimeWindow ALL_DAY = new TimeWindow(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);

    private static final int SECONDS_PER_DAY = 24 * 60 * 60;

    public static TimeWindow parse(String start, String end) {
        return new TimeWindow(TimeOfDayParser.parse(start), TimeOfDayParser.parse(end));
    }

    public boolean contains(LocalTime time) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    /**
     * The part of this window from {@code time} until this window ends, e.g. a 9am–10pm window from 6pm is 6pm–10pm.
     * A time before the start begins at the start; a time at or after the end gives an empty result.
     * <p>
     * {@code time} is read on the day this window starts: it only counts as the next day if it's after midnight
     * while the window is still open (e.g. 1am in a 6pm–2am window). Any other time outside the window counts as
     * before the start.
     */
    public Optional<TimeWindow> from(LocalTime time) {
        int from = Math.max(position(time), startSecond());
        int to = endSecond();
        return from < to ? Optional.of(ofSeconds(from, to)) : Optional.empty();
    }

    /**
     * The part of this window from its start until {@code time}, e.g. a 9am–10pm window until 2pm is 9am–2pm.
     * A time after the end stops at the end; a time at or before the start gives an empty result, except that a
     * whole-day window until its own start (e.g. all day until midnight) is still the whole day.
     * <p>
     * {@code time} is read on the day this window starts, as described in {@link #from(LocalTime)}.
     */
    public Optional<TimeWindow> until(LocalTime time) {
        int from = startSecond();
        int position = position(time);
        if (position == from && isWholeDay()) {
            position += SECONDS_PER_DAY;
        }
        int to = Math.min(position, endSecond());
        return to > from ? Optional.of(ofSeconds(from, to)) : Optional.empty();
    }

    // Positions are seconds on a timeline that starts at midnight of the day this window starts,
    // so a window crossing midnight ends after SECONDS_PER_DAY.

    private int startSecond() {
        return start.toSecondOfDay();
    }

    private int endSecond() {
        int startSecond = startSecond();
        int endSecond = end.toSecondOfDay();
        return endSecond <= startSecond ? endSecond + SECONDS_PER_DAY : endSecond;
    }

    private boolean isWholeDay() {
        return endSecond() - startSecond() == SECONDS_PER_DAY;
    }

    /** A time's position: the next day only if it's before the start and the window is still open then. */
    private int position(LocalTime time) {
        int second = time.toSecondOfDay();
        boolean nextDayWhileOpen = second < startSecond() && second + SECONDS_PER_DAY < endSecond();
        return nextDayWhileOpen ? second + SECONDS_PER_DAY : second;
    }

    private static TimeWindow ofSeconds(int from, int to) {
        return new TimeWindow(LocalTime.ofSecondOfDay(from % SECONDS_PER_DAY), LocalTime.ofSecondOfDay(to % SECONDS_PER_DAY));
    }
}
