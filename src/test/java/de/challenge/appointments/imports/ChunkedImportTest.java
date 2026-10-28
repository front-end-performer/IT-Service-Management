package de.challenge.appointments.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.challenge.appointments.IntegrationTest;
import de.challenge.appointments.appointment.AppointmentScheduler;
import de.challenge.appointments.appointment.BookingService;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Imports run in chunks of {@code appointments.import-chunk-size} rows, one transaction and one
 * provider lock per chunk. Results must not depend on chunk boundaries, and a large import must
 * not block bookings of the same provider until it has finished. An abort is recorded as FAILED.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"appointments.import-chunk-size=2", "appointments.lock-timeout-ms=1000"})
class ChunkedImportTest extends IntegrationTest {

    @Autowired
    private ImportService imports;
    @Autowired
    private BookingService booking;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactions;
    /** Real scheduler, except that customer "BOOM" simulates an unexpected failure (e.g. a database error). */
    @MockitoSpyBean
    private AppointmentScheduler scheduler;

    @Test
    void unexpectedFailureMarksTheImportFailedAndReimportIsSafe() throws Exception {
        // Stubbing passes the scheduler's transactional proxy, which demands a transaction (MANDATORY).
        transactions.executeWithoutResult(status -> doThrow(new IllegalStateException("simulated database failure"))
                .when(scheduler).schedule(any(), eq("BOOM"), any(), any(), any()));
        // Chunks of 2: [A,B] commits, [C,BOOM] fails and is rolled back, [D] is never reached.
        String csv = """
                KdnNr,APP_DATE,SERVICE
                A,2026-11-02T08:00Z,DIA
                B,2026-11-02T09:00Z,DIA
                C,2026-11-02T10:00Z,DIA
                BOOM,2026-11-02T11:00Z,DIA
                D,2026-11-02T12:00Z,DIA
                """;

        String location = mvc.perform(multipart("/it-services/IT-MUELLER/appointment-imports")
                        .file(new MockMultipartFile("file", "abort.csv", "text/csv", csv.getBytes())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Import aborted"))
                .andExpect(jsonPath("$.importId").isString())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Import aborted at line 4")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("simulated"))))
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.failureMessage").value(org.hamcrest.Matchers.containsString("re-importing the file is safe")));
        assertThat(jdbc.queryForObject("select count(*) from appointment", Integer.class)).isEqualTo(2);

        // After the cause is gone, the same file (with the failing row fixed) completes; A and B are skipped.
        var retry = imports.importCsv("IT-MUELLER", "abort.csv", new StringReader(csv.replace("BOOM", "E")));
        assertThat(retry.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(retry.getDuplicates()).isEqualTo(2);
        assertThat(retry.getImported()).isEqualTo(3);
    }

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

        assertThat(batch.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.getFailureMessage()).isNull();
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
