package de.challenge.appointments.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.challenge.appointments.scheduling.OpeningHours.Period;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpeningHoursTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Mon-Fri 08:00-18:00, Sat 09:00-13:00, Sunday closed – Berlin wall-clock time. */
    private static final OpeningHours HOURS = OpeningHours.of(BERLIN, Map.of(
            DayOfWeek.MONDAY, Period.parse("08:00-18:00"),
            DayOfWeek.TUESDAY, Period.parse("08:00-18:00"),
            DayOfWeek.WEDNESDAY, Period.parse("08:00-18:00"),
            DayOfWeek.THURSDAY, Period.parse("08:00-18:00"),
            DayOfWeek.FRIDAY, Period.parse("08:00-18:00"),
            DayOfWeek.SATURDAY, Period.parse("09:00-13:00")));

    private static boolean open(String utcStart, int minutes) {
        return HOURS.contains(TimeSlot.of(Instant.parse(utcStart), Duration.ofMinutes(minutes)));
    }

    @Test
    void appointmentMustLieCompletelyWithinTheDay() {
        // Monday 2026-11-02, CET (UTC+1): 08:00-18:00 local = 07:00-17:00Z
        assertThat(open("2026-11-02T07:00:00Z", 30)).isTrue();
        assertThat(open("2026-11-02T06:45:00Z", 30)).as("starts before opening").isFalse();
        assertThat(open("2026-11-02T16:30:00Z", 30)).as("ends exactly at closing").isTrue();
        assertThat(open("2026-11-02T16:00:00Z", 120)).as("runs past closing").isFalse();
    }

    @Test
    void closedDaysAndShorterSaturday() {
        assertThat(open("2026-11-07T08:00:00Z", 30)).as("Saturday 09:00 local").isTrue();
        assertThat(open("2026-11-07T11:30:00Z", 60)).as("Saturday until 13:30 local").isFalse();
        assertThat(open("2026-11-08T09:00:00Z", 30)).as("Sunday").isFalse();
    }

    @Test
    void followsTheDaylightSavingSwitchInAutumn() {
        // Friday 2026-10-23 is still CEST (UTC+2): 08:00 local = 06:00Z.
        assertThat(open("2026-10-23T06:00:00Z", 15)).isTrue();
        // Monday 2026-10-26 is CET (UTC+1) after the switch on 25 October: 06:00Z is 07:00 local.
        assertThat(open("2026-10-26T06:00:00Z", 15)).isFalse();
        assertThat(open("2026-10-26T07:00:00Z", 15)).isTrue();
        assertThat(open("2026-10-26T16:45:00Z", 15)).as("17:45-18:00 local").isTrue();
    }

    @Test
    void followsTheDaylightSavingSwitchInSpring() {
        // Friday 2027-03-26 CET: 08:00 local = 07:00Z; Monday 2027-03-29 CEST: 08:00 local = 06:00Z.
        assertThat(open("2027-03-26T06:00:00Z", 15)).isFalse();
        assertThat(open("2027-03-26T07:00:00Z", 15)).isTrue();
        assertThat(open("2027-03-29T06:00:00Z", 15)).isTrue();
        assertThat(open("2027-03-29T15:45:00Z", 15)).as("17:45-18:00 local").isTrue();
        assertThat(open("2027-03-29T16:00:00Z", 15)).as("18:00 local").isFalse();
    }

    @Test
    void neverAcrossMidnight() {
        var lateShift = OpeningHours.of(BERLIN, Map.of(
                DayOfWeek.MONDAY, Period.parse("20:00-23:59"),
                DayOfWeek.TUESDAY, Period.parse("00:00-06:00")));

        // Monday 23:00 local + 2h would end Tuesday 01:00 – both days are open, still rejected.
        assertThat(lateShift.contains(TimeSlot.of(Instant.parse("2026-11-02T22:00:00Z"), Duration.ofHours(2)))).isFalse();
        assertThat(lateShift.contains(TimeSlot.of(Instant.parse("2026-11-02T22:00:00Z"), Duration.ofMinutes(45)))).isTrue();
    }

    @Test
    void withoutConfigurationAProviderIsAlwaysOpen() {
        assertThat(OpeningHours.ALWAYS.contains(TimeSlot.of(Instant.parse("2026-11-08T02:00:00Z"), Duration.ofHours(3))))
                .isTrue();
    }

    @Test
    void rejectsMalformedPeriods() {
        assertThatThrownBy(() -> Period.parse("18:00-08:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Period.parse("08:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Period.parse("8-18")).isInstanceOf(RuntimeException.class);
    }
}
