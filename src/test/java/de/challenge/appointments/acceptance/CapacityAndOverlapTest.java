package de.challenge.appointments.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import de.challenge.appointments.scheduling.CapacityCalculator;
import de.challenge.appointments.scheduling.TimeSlot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C1–C9, C11–C13: capacity and overlap as domain-level unit tests (no Spring, no database), with the
 * durations from the task: Müller DIA 30, LAP 120, OSI 60 (capacity 2); Schneider DIA 15, DAT 180
 * (capacity 3). Overlap shapes: identical (C2), at the end (C4), at the start (C5), inside (C11),
 * enclosing (C12), touching after (C3) and before (C13). C10 needs two providers in the database and lives in {@link BookingAcceptanceTest}.
 */
class CapacityAndOverlapTest {

    private static final int MUELLER = 2;
    private static final int SCHNEIDER = 3;

    private static TimeSlot dia(String start) {
        return slot(start, 30);
    }

    private static TimeSlot lap(String start) {
        return slot(start, 120);
    }

    private static TimeSlot osi(String start) {
        return slot(start, 60);
    }

    private static TimeSlot slot(String start, int minutes) {
        return TimeSlot.of(Instant.parse("2026-11-02T" + start + ":00Z"), Duration.ofMinutes(minutes));
    }

    @Test
    @DisplayName("C1 Empty schedule, DIA at 08:00 is allowed")
    void C1_emptySchedule() {
        assertThat(CapacityCalculator.fits(List.of(), dia("08:00"), MUELLER)).isTrue();
    }

    @Test
    @DisplayName("C2 Two DIA at 08:00, a third DIA at 08:00 is rejected")
    void C2_thirdAtSameStart() {
        assertThat(CapacityCalculator.fits(List.of(dia("08:00"), dia("08:00")), dia("08:00"), MUELLER)).isFalse();
    }

    @Test
    @DisplayName("C3 Two DIA 08:00-08:30, a new DIA at 08:30 is allowed (back-to-back is no overlap)")
    void C3_backToBack() {
        assertThat(CapacityCalculator.fits(List.of(dia("08:00"), dia("08:00")), dia("08:30"), MUELLER)).isTrue();
    }

    @Test
    @DisplayName("C4 Two LAP 08:00-10:00, a new DIA at 09:45 is rejected (overlap at the end)")
    void C4_overlapAtTheEnd() {
        assertThat(CapacityCalculator.fits(List.of(lap("08:00"), lap("08:00")), dia("09:45"), MUELLER)).isFalse();
    }

    @Test
    @DisplayName("C5 Two LAP 08:00-10:00, a new DIA at 07:45 is rejected (overlap at the start)")
    void C5_overlapAtTheStart() {
        assertThat(CapacityCalculator.fits(List.of(lap("08:00"), lap("08:00")), dia("07:45"), MUELLER)).isFalse();
    }

    @Test
    @DisplayName("C6 DIA 08:00, DIA 08:30, OSI 09:00 and a new LAP 08:00-10:00 is allowed (never more than 2)")
    void C6_sequentialAppointmentsUnderOneLongOne() {
        var booked = List.of(dia("08:00"), dia("08:30"), osi("09:00"));

        assertThat(CapacityCalculator.fits(booked, lap("08:00"), MUELLER)).isTrue();
    }

    @Test
    @DisplayName("C7 OSI 08:00-09:00, OSI 08:30-09:30 and a new DIA at 08:30 is rejected (3 from 08:30 to 09:00)")
    void C7_threeConcurrentInTheMiddle() {
        assertThat(CapacityCalculator.fits(List.of(osi("08:00"), osi("08:30")), dia("08:30"), MUELLER)).isFalse();
    }

    @Test
    @DisplayName("C8 Schneider: three DAT 08:00-11:00, a new DIA at 10:45 is rejected")
    void C8_schneiderFull() {
        var booked = List.of(slot("08:00", 180), slot("08:00", 180), slot("08:00", 180));

        assertThat(CapacityCalculator.fits(booked, slot("10:45", 15), SCHNEIDER)).isFalse();
    }

    @Test
    @DisplayName("C9 Schneider: the third parallel booking is allowed, the fourth is rejected")
    void C9_schneiderThirdAndFourth() {
        var two = List.of(slot("08:00", 15), slot("08:00", 15));
        var three = List.of(slot("08:00", 15), slot("08:00", 15), slot("08:00", 15));

        assertThat(CapacityCalculator.fits(two, slot("08:00", 15), SCHNEIDER)).isTrue();
        assertThat(CapacityCalculator.fits(three, slot("08:00", 15), SCHNEIDER)).isFalse();
    }

    @Test
    @DisplayName("C11 Two LAP 08:00-10:00, a new DIA at 09:00 (fully inside) is rejected")
    void C11_newInsideExisting() {
        assertThat(CapacityCalculator.fits(List.of(lap("08:00"), lap("08:00")), dia("09:00"), MUELLER)).isFalse();
    }

    @Test
    @DisplayName("C12 Two DIA at 09:00, a new LAP at 08:00 (fully enclosing them) is rejected")
    void C12_newEnclosesExisting() {
        // Catches the classic bug of only checking whether the new start or end lies inside an existing appointment.
        assertThat(CapacityCalculator.fits(List.of(dia("09:00"), dia("09:00")), lap("08:00"), MUELLER)).isFalse();
    }

    @Test
    @DisplayName("C13 Two LAP 08:00-10:00, a new DIA at 07:30 (ending exactly at their start) is allowed")
    void C13_touchingBefore() {
        assertThat(CapacityCalculator.fits(List.of(lap("08:00"), lap("08:00")), dia("07:30"), MUELLER)).isTrue();
    }
}
