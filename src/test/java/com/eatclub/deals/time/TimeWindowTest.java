package com.eatclub.deals.time;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWindowTest {

    @Test
    void startIsInclusiveAndEndIsExclusive() {
        TimeWindow window = TimeWindow.parse("3:00pm", "9:00pm");

        assertThat(window.contains(LocalTime.of(14, 59))).isFalse();
        assertThat(window.contains(LocalTime.of(15, 0))).isTrue();
        assertThat(window.contains(LocalTime.of(20, 59))).isTrue();
        assertThat(window.contains(LocalTime.of(21, 0))).isFalse();
    }

    @Test
    void supportsWindowsCrossingMidnight() {
        TimeWindow window = TimeWindow.parse("6:00pm", "2:00am");

        assertThat(window.contains(LocalTime.of(17, 59))).isFalse();
        assertThat(window.contains(LocalTime.of(23, 0))).isTrue();
        assertThat(window.contains(LocalTime.of(1, 59))).isTrue();
        assertThat(window.contains(LocalTime.of(2, 0))).isFalse();
    }

    @Test
    void allDayContainsEverything() {
        assertThat(TimeWindow.ALL_DAY.contains(LocalTime.MIDNIGHT)).isTrue();
        assertThat(TimeWindow.ALL_DAY.contains(LocalTime.of(23, 59))).isTrue();
    }
}
