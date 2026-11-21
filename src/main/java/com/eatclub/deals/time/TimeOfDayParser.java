package com.eatclub.deals.time;

import com.eatclub.deals.exception.InvalidTimeOfDayException;

import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses wall-clock times. Leading and trailing spaces are trimmed first; the hour and minutes are always
 * separated by {@code :}, and minutes always have two digits.
 * <ul>
 *   <li>12-hour: hour 1–12, minutes, then {@code am} or {@code pm} with no space, in upper or lower case:
 *       {@code 3:00pm}, {@code 10:30AM}, {@code 12:00am} (midnight), {@code 12:00pm} (noon).</li>
 *   <li>24-hour: hour 0–23 and minutes, with no suffix: {@code 10:30}, {@code 09:05}, {@code 15:00}.</li>
 * </ul>
 * Rejected, for example: {@code 3pm} (no minutes), {@code 3:00 pm} (space before the suffix),
 * {@code 3:00p.m.} (dots), {@code 3.00pm} (wrong separator), {@code 3} (no minutes).
 */
public final class TimeOfDayParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{2})([ap]m)?$", Pattern.CASE_INSENSITIVE);

    private TimeOfDayParser() {
    }

    /** Parses a time that doesn't come from a request parameter (e.g. hours in the upstream feed). */
    public static LocalTime parse(String raw) {
        return parse(raw, null);
    }

    /**
     * Parses a time taken from the given request parameter; the parameter name is reported in the error.
     *
     * @throws InvalidTimeOfDayException if {@code raw} is not a supported time
     */
    public static LocalTime parse(String raw, String parameter) {
        return toLocalTime(raw).orElseThrow(() -> new InvalidTimeOfDayException(raw, parameter));
    }

    /** The time, or empty if {@code raw} isn't a supported time. */
    private static Optional<LocalTime> toLocalTime(String raw) {
        Matcher m = PATTERN.matcher(raw == null ? "" : raw.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        String suffix = m.group(3); // "am", "pm" (any case) or null
        if (minute > 59 || !isValidHour(hour, suffix)) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(to24Hour(hour, suffix), minute));
    }

    /** 12-hour times use hours 1–12; 24-hour times use 0–23. */
    private static boolean isValidHour(int hour, String suffix) {
        return suffix == null ? hour <= 23 : hour >= 1 && hour <= 12;
    }

    /** 12am → 0, 1am → 1, 12pm → 12, 3pm → 15; a 24-hour time is returned unchanged. */
    private static int to24Hour(int hour, String suffix) {
        if (suffix == null) {
            return hour;
        }
        return (hour % 12) + (suffix.equalsIgnoreCase("pm") ? 12 : 0);
    }
}
