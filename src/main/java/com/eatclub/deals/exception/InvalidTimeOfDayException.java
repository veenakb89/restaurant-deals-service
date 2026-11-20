package com.eatclub.deals.exception;

public class InvalidTimeOfDayException extends RuntimeException {

    public InvalidTimeOfDayException(String value) {
        super("Invalid time of day: '" + value + "'. Expected a time such as '3:00pm', '3pm' or '15:00'.");
    }
}
