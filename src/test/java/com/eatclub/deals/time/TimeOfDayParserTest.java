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

    @ParameterizedTest(name = "''{0}'' -> {1}")
    @CsvSource({
            // 12-hour: h:mm + am/pm, no space, upper or lower case
            "3:00pm, 15:00",
            "3:00PM, 15:00",
            "10:30am, 10:30",
            "10:30AM, 10:30",
            "9:00pm, 21:00",
            "12:00am, 00:00",   // midnight
            "12:30am, 00:30",
            "12:00pm, 12:00",   // noon
            "11:59pm, 23:59",
            // 24-hour: h:mm or hh:mm, no suffix
            "10:30, 10:30",
            "09:05, 09:05",
            "9:05, 09:05",
            "00:00, 00:00",
            "15:00, 15:00",
            "23:59, 23:59",
            // leading and trailing spaces are trimmed
            "'  3:00pm  ', 15:00",
            "'  10:30  ', 10:30"
    })
    void parsesSupportedFormats(String input, String expected) {
        assertThat(TimeOfDayParser.parse(input)).isEqualTo(LocalTime.parse(expected));
    }

    @ParameterizedTest(name = "''{0}'' is rejected")
    @ValueSource(strings = {
            "", " ", "abc", "noon",
            "3pm", "3PM", "12pm",                 // 12-hour time without minutes
            "3:00 pm", "3:00 PM",                 // space before am/pm
            "3:00p.m.", "3:00 p.m.", "3:00a", "3:00p", "3:00m", "3:00pmm",
            "3.00pm", "3-00pm", "15.00",          // separator other than ':'
            "3 :00pm", "3: 00pm",                 // spaces around ':'
            "3", "15",                            // no minutes
            "3:0pm", "3:000pm",                   // minutes must be two digits
            "3:00:00",                            // seconds
            "13:00pm", "0:00am",                  // 12-hour hour out of range
            "24:00", "25:00", "10:60", "-1:00", "123:00"
    })
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
