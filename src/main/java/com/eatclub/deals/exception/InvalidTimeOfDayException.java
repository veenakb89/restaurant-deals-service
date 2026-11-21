package com.eatclub.deals.exception;

/**
 * A value could not be parsed as a time of day.
 * <p>
 * {@link #parameter()} names the request parameter that held the value, or is {@code null} when the
 * value didn't come from a request (e.g. hours in the upstream feed).
 */
public class InvalidTimeOfDayException extends RuntimeException {

    private final String parameter;

    public InvalidTimeOfDayException(String value, String parameter) {
        super("Invalid time of day: '" + value + "'. Expected a time such as '3:00pm', '10:30AM' or '15:00'.");
        this.parameter = parameter;
    }

    public String parameter() {
        return parameter;
    }
}
