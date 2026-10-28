package de.challenge.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.challenge.appointments.appointment.BookingService;
import de.challenge.appointments.common.ConflictException;
import de.challenge.appointments.imports.ImportService;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The alternative H2 setup ({@code --spring.profiles.active=h2}): same Flyway migrations, same
 * import, same capacity rules and the same row lock against concurrent bookings.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:h2profile;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("h2")
@Import(FixedClockConfiguration.class)
class H2ProfileTest {

    @Autowired
    private BookingService booking;
    @Autowired
    private ImportService imports;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void runsTheWholeSchedulingFlowOnH2() throws Exception {
        String csv = Files.readString(Path.of("termine/Computer-Service-Schneider.csv"));
        assertThat(imports.importCsv("IT-MUELLER", "legacy.csv", new StringReader(csv)).getImported()).isEqualTo(20);

        // 2026-11-02 09:15-09:45 is already at capacity 2 through imported appointments.
        assertThatThrownBy(() -> booking.book("IT-MUELLER", "DIA", "C-1", Instant.parse("2026-11-02T09:15:00Z")))
                .isInstanceOf(ConflictException.class);

        Instant slot = Instant.parse("2026-11-10T09:00:00Z");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String customer = "P-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        booking.book("IT-MUELLER", "LAP", customer, slot);
                        succeeded.incrementAndGet();
                    } catch (ConflictException expected) {
                        // lost the race
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
        assertThat(succeeded.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from appointment where start_at = ?", Integer.class,
                java.sql.Timestamp.from(slot))).isEqualTo(2);
    }
}
