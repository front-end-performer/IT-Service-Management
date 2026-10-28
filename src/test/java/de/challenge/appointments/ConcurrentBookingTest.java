package de.challenge.appointments;

import static org.assertj.core.api.Assertions.assertThat;

import de.challenge.appointments.appointment.BookingService;
import de.challenge.appointments.common.ConflictException;
import de.challenge.appointments.scheduling.CapacityCalculator;
import de.challenge.appointments.scheduling.TimeSlot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fires many bookings at the same time against PostgreSQL and checks that the provider lock
 * prevents overbooking.
 */
class ConcurrentBookingTest extends IntegrationTest {

    private static final Instant NINE = Instant.parse("2026-11-02T09:00:00Z");

    @Autowired
    private BookingService booking;

    @Test
    void parallelBookingsOfTheSameSlotNeverExceedCapacity() throws Exception {
        int attempts = 20;
        var outcome = runConcurrently(attempts, i -> () -> booking.book("IT-MUELLER", "LAP", "C-" + i, NINE));

        assertThat(outcome.succeeded()).isEqualTo(2); // IT-Service Müller: capacity 2
        assertThat(outcome.conflicts()).isEqualTo(attempts - 2);
        assertPeakConcurrencyWithinCapacity("IT-MUELLER", 2);
    }

    @Test
    void parallelOverlappingBookingsWithDifferentStartsNeverExceedCapacity() throws Exception {
        String[] services = {"DIA", "VIR", "DAT"};
        // 60 requests over 09:00-11:45 for Schneider (capacity 3) with durations 15/60/180 min.
        var outcome = runConcurrently(60, i -> () -> booking.book("CS-SCHNEIDER", services[i % 3], "C-" + i,
                NINE.plusSeconds(15 * 60L * (i % 12))));

        assertThat(outcome.succeeded()).isPositive();
        assertThat(outcome.succeeded() + outcome.conflicts()).isEqualTo(60);
        assertPeakConcurrencyWithinCapacity("CS-SCHNEIDER", 3);
    }

    private record Outcome(int succeeded, int conflicts) {
    }

    private interface TaskFactory {
        Callable<Object> create(int index);
    }

    private Outcome runConcurrently(int count, TaskFactory tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Object> task = tasks.create(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        succeeded.incrementAndGet();
                    } catch (ConflictException e) {
                        conflicts.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(); // surfaces unexpected exceptions
            }
        }
        return new Outcome(succeeded.get(), conflicts.get());
    }

    private void assertPeakConcurrencyWithinCapacity(String providerId, int capacity) {
        List<TimeSlot> stored = jdbc.query(
                "select start_at, end_at from appointment where provider_id = ?",
                (rs, n) -> new TimeSlot(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()),
                providerId);
        TimeSlot wholeDay = new TimeSlot(Instant.parse("2026-11-02T00:00:00Z"), Instant.parse("2026-11-03T00:00:00Z"));
        assertThat(CapacityCalculator.peakConcurrency(stored, wholeDay)).isBetween(1, capacity);
    }
}
