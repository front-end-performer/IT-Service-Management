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
        var free = SlotFinder.findFreeSlots(at("08:00"), at("09:00"), Duration.ofMinutes(30), List.of(), 1);

        assertThat(starts(free)).containsExactly(at("08:00"), at("08:15"), at("08:30"));
        assertThat(free.getLast().end()).isEqualTo(at("09:00"));
    }

    @Test
    void searchStartIsRoundedUpToTheGrid() {
        var free = SlotFinder.findFreeSlots(at("08:07"), at("09:00"), Duration.ofMinutes(15), List.of(), 1);

        assertThat(starts(free)).containsExactly(at("08:15"), at("08:30"), at("08:45"));
    }

    @Test
    void rangeShorterThanServiceYieldsNothing() {
        var free = SlotFinder.findFreeSlots(at("08:00"), at("09:00"), Duration.ofMinutes(120), List.of(), 2);

        assertThat(free).isEmpty();
    }

    @Test
    void muellerCapacityTwoBlocksSlotsWhereTwoAppointmentsRun() {
        // IT-Service Müller, capacity 2: two laptop services 09:00-11:00.
        var booked = List.of(slot("09:00", 120), slot("09:00", 120));

        var free = SlotFinder.findFreeSlots(at("08:00"), at("12:00"), Duration.ofMinutes(60), booked, 2);

        // 08:00 ends exactly when the LAPs start; 11:00 starts exactly when they end.
        assertThat(starts(free)).containsExactly(at("08:00"), at("11:00"));
    }

    @Test
    void schneiderCapacityThreeAllowsAThirdParallelAppointment() {
        // Computer-Service Schneider, capacity 3: two data recoveries 08:00-11:00.
        var booked = List.of(slot("08:00", 180), slot("08:00", 180));

        var free = SlotFinder.findFreeSlots(at("08:00"), at("09:00"), Duration.ofMinutes(60), booked, 3);

        assertThat(starts(free)).containsExactly(at("08:00"));
    }

    @Test
    void slotIsRejectedWhenCapacityIsExceededLaterInTheDuration() {
        // Capacity 2, one long appointment all morning and one short at 09:30.
        var booked = List.of(slot("08:00", 240), slot("09:30", 30));

        var free = SlotFinder.findFreeSlots(at("08:00"), at("12:00"), Duration.ofMinutes(120), booked, 2);

        // Any 2h candidate covering 09:30-10:00 would be the third one.
        assertThat(starts(free)).containsExactly(at("10:00"));
    }
}
