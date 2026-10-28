package de.challenge.appointments.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Lists every grid-aligned start in a search range at which a service is within opening hours and still fits. */
public final class SlotFinder {

    private SlotFinder() {
    }

    /**
     * @param from      earliest start (rounded up to the grid)
     * @param to        latest end; a suggestion must be completed by then
     * @param duration  service duration
     * @param booked    existing appointments of the provider (booked and imported)
     * @param capacity  maximum concurrent appointments of the provider
     * @param hours     opening hours of the provider; the whole appointment must lie within them
     */
    public static List<TimeSlot> findFreeSlots(Instant from, Instant to, Duration duration,
                                               Collection<TimeSlot> booked, int capacity, OpeningHours hours) {
        List<TimeSlot> free = new ArrayList<>();
        for (Instant start = TimeGrid.ceil(from); !start.plus(duration).isAfter(to); start = start.plus(TimeGrid.STEP)) {
            TimeSlot candidate = TimeSlot.of(start, duration);
            if (hours.contains(candidate) && CapacityCalculator.fits(booked, candidate, capacity)) {
                free.add(candidate);
            }
        }
        return free;
    }
}
