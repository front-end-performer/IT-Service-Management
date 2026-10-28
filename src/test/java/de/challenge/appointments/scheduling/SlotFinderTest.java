package de.challenge.appointments.scheduling;

import static de.challenge.appointments.scheduling.Slots.at;
import static de.challenge.appointments.scheduling.Slots.slot;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotFinderTest {

    private static List<Instant> starts(List<TimeSlot> slots) {
        return slots.stream().map(TimeSlot::start).toList();
    }

    @Test
    void emptyScheduleYieldsEveryQuarterHourThatFitsTheRange() {
        var free = SlotFinder.findFreeSlots(at("08:00"), at("09:00"), Duration.ofMinutes(30), List.of(), 1, OpeningHours.ALWAYS);

        assertThat(starts(free)).containsExactly(at("08:00"), at("08:15"), at("08:30"));
        assertThat(free.getLast().end()).isEqualTo(at("09:00"));
    }

    @Test
    void searchStartIsRoundedUpToTheGrid() {
        var free = SlotFinder.findFreeSlots(at("08:07"), at("09:00"), Duration.ofMinutes(15), List.of(), 1, OpeningHours.ALWAYS);

        assertThat(starts(free)).containsExactly(at("08:15"), at("08:30"), at("08:45"));
    }

    @Test
    void rangeShorterThanServiceYieldsNothing() {
        var free = SlotFinder.findFreeSlots(at("08:00"), at("09:00"), Duration.ofMinutes(120), List.of(), 2, OpeningHours.ALWAYS);

        assertThat(free).isEmpty();
    }

    @Test
    void muellerCapacityTwoBlocksSlotsWhereTwoAppointmentsRun() {
        // IT-Service Müller, capacity 2: two laptop services 09:00-11:00.
        var booked = List.of(slot("09:00", 120), slot("09:00", 120));

        var free = SlotFinder.findFreeSlots(at("08:00"), at("12:00"), Duration.ofMinutes(60), booked, 2, OpeningHours.ALWAYS);

        // 08:00 ends exactly when the LAPs start; 11:00 starts exactly when they end.
        assertThat(starts(free)).containsExactly(at("08:00"), at("11:00"));
    }

    @Test
    void schneiderCapacityThreeAllowsAThirdParallelAppointment() {
        // Computer-Service Schneider, capacity 3: two data recoveries 08:00-11:00.
        var booked = List.of(slot("08:00", 180), slot("08:00", 180));

        var free = SlotFinder.findFreeSlots(at("08:00"), at("09:00"), Duration.ofMinutes(60), booked, 3, OpeningHours.ALWAYS);

        assertThat(starts(free)).containsExactly(at("08:00"));
    }

    @Test
    void slotIsRejectedWhenCapacityIsExceededLaterInTheDuration() {
        // Capacity 2, one long appointment all morning and one short at 09:30.
        var booked = List.of(slot("08:00", 240), slot("09:30", 30));

        var free = SlotFinder.findFreeSlots(at("08:00"), at("12:00"), Duration.ofMinutes(120), booked, 2, OpeningHours.ALWAYS);

        // Any 2h candidate covering 09:30-10:00 would be the third one.
        assertThat(starts(free)).containsExactly(at("10:00"));
    }

    @Test
    void durationsThatAreNotMultiplesOfFifteenMinutesWork() {
        // Starts stay on the grid, ends need not: a 20-minute service 08:00-08:20 blocks the 08:15 start.
        var booked = List.of(slot("08:00", 20));

        var free = SlotFinder.findFreeSlots(at("08:00"), at("09:00"), Duration.ofMinutes(20), booked, 1, OpeningHours.ALWAYS);

        assertThat(starts(free)).containsExactly(at("08:30"));
        assertThat(free.getFirst().end()).isEqualTo(at("08:50"));
    }

    @Test
    void onlySuggestsSlotsCompletelyWithinOpeningHours() {
        // 09:00-11:00 Berlin on Monday 2026-11-02 (CET, UTC+1) = 08:00-10:00Z
        var hours = OpeningHours.of(java.time.ZoneId.of("Europe/Berlin"),
                java.util.Map.of(java.time.DayOfWeek.MONDAY, OpeningHours.Period.parse("09:00-11:00")));

        var free = SlotFinder.findFreeSlots(at("06:00"), at("12:00"), Duration.ofMinutes(60), List.of(), 1, hours);

        assertThat(starts(free)).containsExactly(at("08:00"), at("08:15"), at("08:30"), at("08:45"), at("09:00"));
        assertThat(free.getLast().end()).isEqualTo(at("10:00")); // ends exactly at closing time
    }
}
