package de.challenge.appointments.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.challenge.appointments.IntegrationTest;
import de.challenge.appointments.appointment.BookingService;
import de.challenge.appointments.catalog.CatalogProperties.ProviderConfig;
import de.challenge.appointments.catalog.CatalogProperties.ServiceConfig;
import de.challenge.appointments.common.ConflictException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A new provider and changed durations come from configuration only – no code change – and
 * already booked appointments keep their end time when a duration changes.
 */
class CatalogExtensibilityTest extends IntegrationTest {

    private static final String NEW_PROVIDER = "PC-HILFE-TEST";

    @Autowired
    private CatalogProperties configured;
    @Autowired
    private ProviderRepository providers;
    @Autowired
    private ServiceOfferingRepository services;
    @Autowired
    private BookingService booking;
    @Autowired
    private TransactionTemplate transactions;

    private void applyCatalog(CatalogProperties properties) {
        var initializer = new CatalogInitializer(properties, providers, services);
        transactions.executeWithoutResult(status -> initializer.run(null));
    }

    @AfterEach
    void restoreConfiguredCatalog() {
        jdbc.update("delete from appointment");
        jdbc.update("delete from service_offering where provider_id = ?", NEW_PROVIDER);
        jdbc.update("delete from provider where id = ?", NEW_PROVIDER);
        applyCatalog(configured);
    }

    @Test
    void addsProviderAndChangesDurationFromConfigurationOnly() {
        var existing = booking.book("IT-MUELLER", "DIA", "C-1", Instant.parse("2026-11-02T08:00:00Z"));
        assertThat(existing.getEnd()).isEqualTo(Instant.parse("2026-11-02T08:30:00Z"));

        List<ProviderConfig> changed = new ArrayList<>();
        for (ProviderConfig provider : configured.providers()) {
            changed.add(!provider.id().equals("IT-MUELLER") ? provider : new ProviderConfig(provider.id(),
                    provider.name(), provider.capacity(), provider.services().stream()
                    .map(s -> s.code().equals("DIA") ? new ServiceConfig("DIA", s.name(), 45) : s).toList()));
        }
        changed.add(new ProviderConfig(NEW_PROVIDER, "PC-Hilfe Test", 1,
                List.of(new ServiceConfig("DIA", "PC-Diagnose", 20))));
        applyCatalog(new CatalogProperties(changed));

        // Existing appointment keeps its agreed end; new bookings use the new duration.
        assertThat(jdbc.queryForObject("select end_at from appointment where id = ?", java.sql.Timestamp.class,
                existing.getId()).toInstant()).isEqualTo(Instant.parse("2026-11-02T08:30:00Z"));
        assertThat(booking.book("IT-MUELLER", "DIA", "C-2", Instant.parse("2026-11-02T09:00:00Z")).getEnd())
                .isEqualTo(Instant.parse("2026-11-02T09:45:00Z"));

        // The new provider is fully usable, including its capacity of 1 and a 20-minute service.
        Instant nine = Instant.parse("2026-11-02T09:00:00Z");
        assertThat(booking.book(NEW_PROVIDER, "DIA", "C-3", nine).getEnd())
                .isEqualTo(Instant.parse("2026-11-02T09:20:00Z"));
        assertThatThrownBy(() -> booking.book(NEW_PROVIDER, "DIA", "C-4", nine.plusSeconds(15 * 60)))
                .isInstanceOf(ConflictException.class);
        assertThat(booking.book(NEW_PROVIDER, "DIA", "C-4", nine.plusSeconds(30 * 60))).isNotNull();
    }
}
