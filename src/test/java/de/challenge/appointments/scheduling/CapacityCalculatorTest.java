package de.challenge.appointments.scheduling;

import static de.challenge.appointments.scheduling.Slots.slot;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CapacityCalculatorTest {

    @Test
    void emptyScheduleHasNoLoad() {
        assertThat(CapacityCalculator.peakConcurrency(List.of(), slot("08:00", 60))).isZero();
        assertThat(CapacityCalculator.fits(List.of(), slot("08:00", 60), 1)).isTrue();
    }

    @Test
    void touchingAppointmentsDoNotOverlap() {
        var booked = List.of(slot("08:00", 60), slot("10:00", 30));

        assertThat(CapacityCalculator.peakConcurrency(booked, slot("09:00", 60))).isZero();
        assertThat(CapacityCalculator.fits(booked, slot("09:00", 60), 1)).isTrue();
    }

    @Test
    void capacityReachedAtStartRejectsCandidate() {
        var booked = List.of(slot("08:00", 30), slot("08:00", 120));

        assertThat(CapacityCalculator.fits(booked, slot("08:00", 30), 2)).isFalse();
        assertThat(CapacityCalculator.fits(booked, slot("08:00", 30), 3)).isTrue();
    }

    @Test
    void overlapLaterInTheServiceDurationIsDetected() {
        // Candidate 08:00-10:00 is free at 08:00, but two appointments run in parallel at 09:30.
        var booked = List.of(slot("09:30", 30), slot("09:15", 60));

        assertThat(CapacityCalculator.peakConcurrency(booked, slot("08:00", 120))).isEqualTo(2);
        assertThat(CapacityCalculator.fits(booked, slot("08:00", 120), 2)).isFalse();
    }

    @Test
    void sequentialShortAppointmentsCountAsOneInParallel() {
        // A long candidate spanning several back-to-back short appointments only sees load 1.
        var booked = List.of(slot("08:00", 30), slot("08:30", 30), slot("09:00", 30), slot("09:30", 30));

        assertThat(CapacityCalculator.peakConcurrency(booked, slot("08:00", 120))).isEqualTo(1);
        assertThat(CapacityCalculator.fits(booked, slot("08:00", 120), 2)).isTrue();
    }

    @Test
    void appointmentsOverlappingWindowButNotEachOtherAreNotStacked() {
        // Both overlap the window [08:00, 10:00), but never at the same time.
        var booked = List.of(slot("07:00", 75), slot("09:30", 60));

        assertThat(CapacityCalculator.peakConcurrency(booked, slot("08:00", 120))).isEqualTo(1);
    }

    @Test
    void appointmentStartedBeforeWindowStillCounts() {
        var booked = List.of(slot("07:00", 180), slot("07:30", 90));

        assertThat(CapacityCalculator.peakConcurrency(booked, slot("08:30", 15))).isEqualTo(2);
    }

    @Test
    void appointmentsOutsideWindowAreIgnored() {
        var booked = List.of(slot("06:00", 60), slot("06:00", 60), slot("12:00", 60));

        assertThat(CapacityCalculator.peakConcurrency(booked, slot("08:00", 60))).isZero();
    }

    @Test
    void peakIsTheMaximumNotTheSum() {
        // Three overlapping pairs over time, never three at once.
        var booked = List.of(slot("08:00", 60), slot("08:30", 60), slot("09:15", 60), slot("10:00", 30));

        assertThat(CapacityCalculator.peakConcurrency(booked, slot("08:00", 180))).isEqualTo(2);
    }
}
