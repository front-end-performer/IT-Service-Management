package de.challenge.appointments.imports;

import static org.assertj.core.api.Assertions.assertThat;

import de.challenge.appointments.IntegrationTest;
import de.challenge.appointments.appointment.BookingService;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Imports run in chunks of {@code appointments.import-chunk-size} rows, one transaction and one
 * provider lock per chunk. Results must not depend on chunk boundaries, and a large import must
 * not block bookings of the same provider until it has finished.
 */
@TestPropertySource(properties = {"appointments.import-chunk-size=2", "appointments.lock-timeout-ms=1000"})
class ChunkedImportTest extends IntegrationTest {

    @Autowired
    private ImportService imports;
    @Autowired
    private BookingService booking;

    @Test
    void rulesHoldAcrossChunkBoundaries() throws Exception {
        // Chunks: [1,2] [3,4] [5,6] – capacity 2 is exceeded by row 3 (next chunk), the duplicate is row 6.
        var batch = imports.importCsv("IT-MUELLER", "chunks.csv", new StringReader("""
                KdnNr,APP_DATE,SERVICE
                A,2026-11-02T08:00Z,DIA
                B,2026-11-02T08:00Z,DIA
                C,2026-11-02T08:15Z,DIA
                D,2026-11-02T08:10Z,DIA
                E,2026-11-02T08:30Z,DIA
                A,2026-11-02T08:00Z,DIA
                """));

        assertThat(batch.getTotalRows()).isEqualTo(6);
        assertThat(batch.getImported()).isEqualTo(3);
        assertThat(batch.getRejections()).extracting(ImportRejection::getLineNumber, ImportRejection::getReason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(4, RejectionReason.CAPACITY_EXCEEDED),
                        org.assertj.core.groups.Tuple.tuple(5, RejectionReason.OFF_GRID),
                        org.assertj.core.groups.Tuple.tuple(7, RejectionReason.DUPLICATE));
    }

    @Test
    void largeImportDoesNotBlockBookingsOfTheSameProvider() throws Exception {
        // 1,600 valid rows on weekdays: a DIA (30 min) at every quarter hour 07:00-16:30Z = 08:00-17:30 Berlin,
        // so it ends by Müller's closing time (18:00) and at most 2 run at once (capacity 2).
        StringBuilder csv = new StringBuilder("KdnNr,APP_DATE,SERVICE\n");
        int rows = 0;
        for (LocalDate day = LocalDate.of(2026, 11, 9); rows < 1600; day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() > 5) {
                continue;
            }
            for (int slot = 0; slot < 39 && rows < 1600; slot++) {
                Instant start = day.atTime(7, 0).toInstant(java.time.ZoneOffset.UTC).plus(Duration.ofMinutes(15L * slot));
                csv.append("L-").append(rows).append(',').append(start).append(",DIA\n");
                rows++;
            }
        }
        CompletableFuture<ImportBatch> running = CompletableFuture.supplyAsync(() -> {
            try {
                return imports.importCsv("IT-MUELLER", "large.csv", new StringReader(csv.toString()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // Let the import get going, then book for the same provider (on a day outside the imported range). With one transaction for the
        // whole file this would wait longer than the 1 s lock timeout and fail with a lock timeout.
        while (jdbc.queryForObject("select count(*) from appointment", Integer.class) < 50) {
            Thread.onSpinWait();
        }
        long started = System.nanoTime();
        booking.book("IT-MUELLER", "OSI", "DURING-IMPORT", Instant.parse("2027-02-01T09:00:00Z"));
        long waitedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(running.isDone()).as("import still running while the booking succeeded").isFalse();
        assertThat(waitedMillis).isLessThan(1000);
        assertThat(running.get().getImported()).isEqualTo(1600);
    }
}
