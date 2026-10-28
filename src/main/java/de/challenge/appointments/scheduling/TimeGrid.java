package de.challenge.appointments.scheduling;

import java.time.Duration;
import java.time.Instant;

/** Appointments may only start on a 15-minute grid (…:00, :15, :30, :45 UTC). */
public final class TimeGrid {

    public static final Duration STEP = Duration.ofMinutes(15);
    private static final long STEP_SECONDS = STEP.toSeconds();

    private TimeGrid() {
    }

    public static boolean isAligned(Instant instant) {
        return instant.getNano() == 0 && Math.floorMod(instant.getEpochSecond(), STEP_SECONDS) == 0;
    }

    /** Smallest grid point that is not before {@code instant}. */
    public static Instant ceil(Instant instant) {
        if (isAligned(instant)) {
            return instant;
        }
        long floor = Math.floorDiv(instant.getEpochSecond(), STEP_SECONDS) * STEP_SECONDS;
        return Instant.ofEpochSecond(floor + STEP_SECONDS);
    }
}
