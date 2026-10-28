package de.challenge.appointments.scheduling;

import java.time.Duration;
import java.time.Instant;

/** Test shorthand: {@code at("08:15")} is 2026-11-02T08:15Z. */
final class Slots {

    static final String DAY = "2026-11-02T";

    private Slots() {
    }

    static Instant at(String hhmm) {
        return Instant.parse(DAY + hhmm + ":00Z");
    }

    static TimeSlot slot(String startHhmm, int minutes) {
        return TimeSlot.of(at(startHhmm), Duration.ofMinutes(minutes));
    }
}
