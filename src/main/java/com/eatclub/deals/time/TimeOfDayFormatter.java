package com.eatclub.deals.time;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formats times the same way the upstream feed writes them: 12-hour, two-digit minutes and a lowercase
 * {@code am}/{@code pm} with no space, e.g. {@code 6:00pm}, {@code 12:00am} (midnight). The result can be passed
 * back to the API as {@code timeOfDay}.
 */
public final class TimeOfDayFormatter {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);

    private TimeOfDayFormatter() {
    }

    public static String format(LocalTime time) {
        return FORMAT.format(time).toLowerCase(Locale.ROOT);
    }
}
