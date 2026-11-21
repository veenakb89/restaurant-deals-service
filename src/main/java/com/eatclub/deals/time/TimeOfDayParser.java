package com.eatclub.deals.time;

import com.eatclub.deals.exception.InvalidTimeOfDayException;

import java.time.LocalTime;
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
        if (raw == null) {
            throw new InvalidTimeOfDayException(raw, parameter);
        }
        Matcher m = PATTERN.matcher(raw.trim());
        if (!m.matches()) {
            throw new InvalidTimeOfDayException(raw, parameter);
        }

        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        String suffix = m.group(3);

        if (minute > 59) {
            throw new InvalidTimeOfDayException(raw, parameter);
        }
        if (suffix != null) {
            if (hour < 1 || hour > 12) {
                throw new InvalidTimeOfDayException(raw, parameter);
            }
            boolean pm = suffix.equalsIgnoreCase("pm");
            hour = (hour % 12) + (pm ? 12 : 0);
        } else if (hour > 23) {
            throw new InvalidTimeOfDayException(raw, parameter);
        }
        return LocalTime.of(hour, minute);
    }
}
