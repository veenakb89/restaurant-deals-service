package com.eatclub.deals.time;

import java.time.LocalTime;

/**
 * A daily time window. The start is inclusive and the end is exclusive, so a restaurant
 * open 3:00pm–9:00pm is open at 3:00pm but closed at 9:00pm.
 * <p>
 * A window whose end is earlier than its start crosses midnight (e.g. 6:00pm–2:00am).
 * A window whose start equals its end covers the whole day.
 */
public record TimeWindow(LocalTime start, LocalTime end) {

    public static final TimeWindow ALL_DAY = new TimeWindow(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);

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
}
