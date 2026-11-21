package com.eatclub.deals.time;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeOfDayFormatterTest {

    @ParameterizedTest(name = "{0} is formatted as {1}")
    @CsvSource({
            "00:00, 12:00am",  // midnight
            "00:30, 12:30am",
            "09:05, 9:05am",
            "12:00, 12:00pm",  // noon
            "18:00, 6:00pm",
            "23:59, 11:59pm"
    })
    void formatsLikeTheFeed(String time, String expected) {
        assertThat(TimeOfDayFormatter.format(LocalTime.parse(time))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} round-trips")
    @CsvSource({"00:00", "09:05", "12:00", "18:00", "23:59"})
    void formattedTimeCanBeParsedBack(String time) {
        LocalTime original = LocalTime.parse(time);

        assertThat(TimeOfDayParser.parse(TimeOfDayFormatter.format(original))).isEqualTo(original);
    }
}
