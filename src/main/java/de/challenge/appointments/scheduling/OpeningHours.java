package de.challenge.appointments.scheduling;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Weekly opening hours of a provider in its local time zone, at most one period per day.
 *
 * <p>An appointment must start and end within the period of its (local) day: it may end exactly
 * at closing time, but never run past closing or across midnight. Wall-clock times are evaluated
 * in the provider's zone, so daylight-saving switches are handled by {@link ZonedDateTime}.
 */
public final class OpeningHours {

    /** No restriction – used when a provider has no opening hours configured. */
    public static final OpeningHours ALWAYS = new OpeningHours(ZoneOffset.UTC, Map.of(), true);

    public record Period(LocalTime opens, LocalTime closes) {

        public Period {
            Objects.requireNonNull(opens, "opens");
            Objects.requireNonNull(closes, "closes");
            if (!closes.isAfter(opens)) {
                throw new IllegalArgumentException("Closing time %s must be after opening time %s".formatted(closes, opens));
            }
        }

        /** Parses {@code "08:00-18:00"}. */
        public static Period parse(String range) {
            String[] parts = range.split("-");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Opening hours must look like 08:00-18:00 but were '%s'".formatted(range));
            }
            return new Period(LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim()));
        }

        @Override
        public String toString() {
            return opens + "-" + closes;
        }
    }

    private final ZoneId zone;
    private final Map<DayOfWeek, Period> periods;
    private final boolean alwaysOpen;

    private OpeningHours(ZoneId zone, Map<DayOfWeek, Period> periods, boolean alwaysOpen) {
        this.zone = zone;
        this.periods = periods;
        this.alwaysOpen = alwaysOpen;
    }

    /** Days missing from {@code periods} are closed. */
    public static OpeningHours of(ZoneId zone, Map<DayOfWeek, Period> periods) {
        Objects.requireNonNull(zone, "zone");
        return new OpeningHours(zone, periods.isEmpty() ? Map.of() : new EnumMap<>(periods), false);
    }

    public boolean contains(TimeSlot slot) {
        if (alwaysOpen) {
            return true;
        }
        ZonedDateTime start = slot.start().atZone(zone);
        ZonedDateTime end = slot.end().atZone(zone);
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return false; // never across midnight
        }
        Period period = periods.get(start.getDayOfWeek());
        return period != null
                && !start.toLocalTime().isBefore(period.opens())
                && !end.toLocalTime().isAfter(period.closes());
    }

    public ZoneId zone() {
        return zone;
    }

    public Map<DayOfWeek, Period> periods() {
        return periods;
    }

    @Override
    public String toString() {
        return alwaysOpen ? "always open" : periods + " " + zone;
    }
}
