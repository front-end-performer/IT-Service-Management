package de.challenge.appointments.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Half-open time interval {@code [start, end)}. An appointment ending at 09:00 does not
 * overlap one starting at 09:00.
 */
public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start: " + start + " / " + end);
        }
    }

    public static TimeSlot of(Instant start, Duration duration) {
        return new TimeSlot(start, start.plus(duration));
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
