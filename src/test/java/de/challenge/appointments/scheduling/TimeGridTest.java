package de.challenge.appointments.scheduling;

import static de.challenge.appointments.scheduling.Slots.at;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimeGridTest {

    @ParameterizedTest
    @ValueSource(strings = {"08:00", "08:15", "08:30", "08:45", "00:00"})
    void quarterHoursAreAligned(String time) {
        assertThat(TimeGrid.isAligned(at(time))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"08:05", "08:10", "08:14", "08:59"})
    void otherMinutesAreNotAligned(String time) {
        assertThat(TimeGrid.isAligned(at(time))).isFalse();
    }

    @Test
    void secondsAndNanosBreakAlignment() {
        assertThat(TimeGrid.isAligned(at("08:00").plusSeconds(1))).isFalse();
        assertThat(TimeGrid.isAligned(at("08:00").plusNanos(1))).isFalse();
    }

    @Test
    void ceilRoundsUpToNextQuarterHour() {
        assertThat(TimeGrid.ceil(at("08:00"))).isEqualTo(at("08:00"));
        assertThat(TimeGrid.ceil(at("08:01"))).isEqualTo(at("08:15"));
        assertThat(TimeGrid.ceil(at("08:44").plusSeconds(59))).isEqualTo(at("08:45"));
        assertThat(TimeGrid.ceil(at("23:50"))).isEqualTo(at("23:45").plusSeconds(15 * 60));
    }
}
