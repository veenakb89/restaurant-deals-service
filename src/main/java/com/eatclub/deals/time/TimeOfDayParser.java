package com.eatclub.deals.time;

import com.eatclub.deals.exception.InvalidTimeOfDayException;

import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses wall-clock times.
 * <p>
 * Accepted inputs (case-insensitive, surrounding whitespace ignored):
 * <ul>
 *   <li>12-hour: {@code 3:00pm}, {@code 3pm}, {@code 3:00 PM}, {@code 10:30a.m.}</li>
 *   <li>24-hour: {@code 10:30}, {@code 15:00}, {@code 09:05}</li>
 * </ul>
 */
public final class TimeOfDayParser {

    private static final Pattern PATTERN =
            Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*([ap])?\\.?\\s*(m\\.?)?$", Pattern.CASE_INSENSITIVE);

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
        if (raw == null || raw.isBlank()) {
            throw new InvalidTimeOfDayException(raw, parameter);
        }
        Matcher m = PATTERN.matcher(raw.trim());
        if (!m.matches()) {
            throw new InvalidTimeOfDayException(raw, parameter);
        }

        int hour = Integer.parseInt(m.group(1));
        String minuteGroup = m.group(2);
        String meridiem = m.group(3);
        boolean hasTrailingM = m.group(4) != null;
        int minute = minuteGroup == null ? 0 : Integer.parseInt(minuteGroup);

        if (minute > 59 || (meridiem == null && hasTrailingM)) {
            throw new InvalidTimeOfDayException(raw, parameter);
        }

        if (meridiem != null) {
            if (hour < 1 || hour > 12) {
                throw new InvalidTimeOfDayException(raw, parameter);
            }
            boolean pm = meridiem.equalsIgnoreCase("p");
            hour = (hour % 12) + (pm ? 12 : 0);
        } else {
            // 24-hour input must include minutes, e.g. "15:00" (a bare "3" is ambiguous).
            if (minuteGroup == null || hour > 23) {
                throw new InvalidTimeOfDayException(raw, parameter);
            }
        }
        return LocalTime.of(hour, minute);
    }
}
