package de.challenge.appointments;

import static org.assertj.core.api.Assertions.assertThat;

import de.challenge.appointments.appointment.BookingService;
import de.challenge.appointments.common.ConflictException;
import de.challenge.appointments.imports.ImportBatch;
import de.challenge.appointments.imports.ImportService;
import de.challenge.appointments.scheduling.CapacityCalculator;
import de.challenge.appointments.scheduling.TimeSlot;
import java.io.StringReader;
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
 * Fires many bookings and imports at the same time against PostgreSQL and checks that the
 * provider lock prevents overbooking and duplicates.
 */
class ConcurrentBookingTest extends IntegrationTest {

    private static final Instant NINE = Instant.parse("2026-11-02T09:00:00Z");

    @Autowired
    private BookingService booking;

    @Autowired
    private ImportService imports;

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

    @Test
    void identicalParallelRequestsCreateExactlyOneAppointment() throws Exception {
        // A double-clicked "Book" button or a client retry: same customer, service and start.
        var outcome = runConcurrently(10, i -> () -> booking.book("CS-SCHNEIDER", "VIR", "C-1", NINE));

        assertThat(outcome.succeeded()).isEqualTo(1);
        assertThat(outcome.conflicts()).isEqualTo(9);
    }

    @Test
    void importRacingWithBookingsNeverExceedsCapacity() throws Exception {
        // Import and bookings target the same LAP slot at Müller (capacity 2); both take the provider lock.
        StringBuilder csv = new StringBuilder("KdnNr,APP_DATE,SERVICE\n");
        for (int i = 0; i < 10; i++) {
            csv.append("IMP-").append(i).append(",2026-11-02T09:00Z,LAP\n");
        }
        AtomicInteger imported = new AtomicInteger();
        var outcome = runConcurrently(11, i -> i == 0
                ? () -> imported.addAndGet(imports.importCsv("IT-MUELLER", "race.csv", new StringReader(csv.toString()))
                        .getImported())
                : () -> booking.book("IT-MUELLER", "LAP", "BOOK-" + i, NINE));

        assertThat(imported.get() + outcome.succeeded() - 1).as("imported + booked").isEqualTo(2);
        assertPeakConcurrencyWithinCapacity("IT-MUELLER", 2);
    }

    @Test
    void parallelImportsOfTheSameFileDoNotCreateDuplicates() throws Exception {
        String csv = """
                KdnNr,APP_DATE,SERVICE
                1,2026-11-02T08:00Z,DIA
                2,2026-11-02T09:00Z,VIR
                3,2026-11-02T10:00Z,DAT
                """;
        List<ImportBatch> batches = java.util.Collections.synchronizedList(new ArrayList<>());
        runConcurrently(5, i -> () -> batches.add(imports.importCsv("CS-SCHNEIDER", "same.csv", new StringReader(csv))));

        assertThat(batches).extracting(ImportBatch::getImported).containsExactlyInAnyOrder(3, 0, 0, 0, 0);
        assertThat(batches).extracting(ImportBatch::getDuplicates).containsExactlyInAnyOrder(0, 3, 3, 3, 3);
        assertThat(jdbc.queryForObject("select count(*) from appointment", Integer.class)).isEqualTo(3);
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
