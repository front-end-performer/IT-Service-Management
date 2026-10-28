package de.challenge.appointments;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Pins "now" to 2026-10-28T12:00Z, shortly before the legacy appointments (2026-11-02 … 11-07),
 * so tests using those dates keep working after the dates have passed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
