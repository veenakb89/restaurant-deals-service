package com.eatclub.deals.time;

import com.eatclub.deals.exception.InvalidTimeOfDayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeOfDayParserTest {

    @ParameterizedTest
    @CsvSource({
            "3:00pm, 15:00",
            "3pm, 15:00",
            "3:00 PM, 15:00",
            "10:30, 10:30",
            "10:30am, 10:30",
            "10:30a.m., 10:30",
            "12:00am, 00:00",
            "12:00pm, 12:00",
            "12:30am, 00:30",
            "9:00pm, 21:00",
            "00:00, 00:00",
            "23:59, 23:59"
    })
    void parsesSupportedFormats(String input, String expected) {
        assertThat(TimeOfDayParser.parse(input)).isEqualTo(LocalTime.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "13:00pm", "0:00am", "24:00", "10:60", "3", "3:00m", "3:0pm"})
    void rejectsInvalidInput(String input) {
        assertThatThrownBy(() -> TimeOfDayParser.parse(input)).isInstanceOf(InvalidTimeOfDayException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> TimeOfDayParser.parse(null)).isInstanceOf(InvalidTimeOfDayException.class);
    }

    @Test
    void reportsTheRequestParameterInTheError() {
        assertThatThrownBy(() -> TimeOfDayParser.parse("abc", "timeOfDay"))
                .isInstanceOfSatisfying(InvalidTimeOfDayException.class,
                        e -> assertThat(e.parameter()).isEqualTo("timeOfDay"));
    }
}
