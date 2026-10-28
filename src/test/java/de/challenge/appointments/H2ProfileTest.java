package de.challenge.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.challenge.appointments.appointment.BookingService;
import de.challenge.appointments.catalog.ProviderRepository;
import de.challenge.appointments.common.ConflictException;
import de.challenge.appointments.imports.ImportService;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Autowired
    private ProviderRepository providers;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void bookingWaitsForTheProviderLockLikeOnPostgres() throws Exception {
        var released = LockProbe.holdProviderLock(transactions, providers, "CS-SCHNEIDER", Duration.ofMillis(1500));
        long started = System.nanoTime();
        booking.book("CS-SCHNEIDER", "DIA", "W-1", Instant.parse("2026-11-11T09:00:00Z"));
        long waitedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        released.get();

        assertThat(waitedMillis).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void runsTheWholeSchedulingFlowOnH2() throws Exception {
        String csv = Files.readString(Path.of("termine/Computer-Service-Schneider.csv"));
        assertThat(imports.importCsv("IT-MUELLER", "legacy.csv", new StringReader(csv)).getImported()).isEqualTo(20);

        // 2026-11-02 09:15-09:45 is already at capacity 2 through imported appointments.
        assertThatThrownBy(() -> booking.book("IT-MUELLER", "DIA", "C-1", Instant.parse("2026-11-02T09:15:00Z")))
                .isInstanceOf(ConflictException.class);

        Instant slot = Instant.parse("2026-11-10T09:00:00Z");
        List<Boolean> booked = Simultaneously.run(10, i -> () -> {
            try {
                booking.book("IT-MUELLER", "LAP", "P-" + i, slot);
                return true;
            } catch (ConflictException lostTheRace) {
                return false;
            }
        });
        assertThat(booked).filteredOn(b -> b).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from appointment where start_at = ?", Integer.class,
                java.sql.Timestamp.from(slot))).isEqualTo(2);
    }
}
