package de.challenge.appointments.scheduling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Determines how many appointments run concurrently and whether one more fits.
 *
 * <p>Concurrency only changes at appointment boundaries, so a sweep over the start/end
 * events of all appointments that overlap the candidate gives the exact peak load inside
 * the candidate's whole duration – not just at its start.
 */
public final class CapacityCalculator {

    private CapacityCalculator() {
    }

    /** Peak number of {@code booked} slots running at the same time within {@code window}. */
    public static int peakConcurrency(Collection<TimeSlot> booked, TimeSlot window) {
        record Event(Instant at, int delta) {
        }
        List<Event> events = new ArrayList<>();
        for (TimeSlot slot : booked) {
            if (slot.overlaps(window)) {
                events.add(new Event(slot.start(), +1));
                events.add(new Event(slot.end(), -1));
            }
        }
        // Ends sort before starts at the same instant: [08:00,09:00) and [09:00,10:00) never run together.
        events.sort(Comparator.comparing(Event::at).thenComparingInt(Event::delta));

        int current = 0;
        int peak = 0;
        for (Event event : events) {
            current += event.delta();
            // Events before the window only build up the load present when the window opens.
            if (event.at().isBefore(window.end())) {
                peak = Math.max(peak, current);
            }
        }
        return peak;
    }

    public static boolean fits(Collection<TimeSlot> booked, TimeSlot candidate, int capacity) {
        return peakConcurrency(booked, candidate) < capacity;
    }
}
