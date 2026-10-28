package de.challenge.appointments.catalog;

import de.challenge.appointments.scheduling.OpeningHours;
import de.challenge.appointments.scheduling.OpeningHours.Period;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Providers and their services as configured under {@code catalog.providers} in application.yml. */
@Validated
@ConfigurationProperties("catalog")
public record CatalogProperties(List<@Valid ProviderConfig> providers) {

    public CatalogProperties {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }

    /**
     * @param timeZone     zone of the opening hours, e.g. Europe/Berlin
     * @param openingHours per weekday, e.g. {@code MONDAY: 08:00-18:00}; missing days are closed,
     *                     no entries at all means open around the clock
     */
    public record ProviderConfig(@NotBlank String id, @NotBlank String name, @Min(1) int capacity,
                                 String timeZone, Map<DayOfWeek, String> openingHours,
                                 List<@Valid ServiceConfig> services) {

        public ProviderConfig {
            openingHours = openingHours == null ? Map.of() : Map.copyOf(openingHours);
            services = services == null ? List.of() : List.copyOf(services);
            buildOpeningHours(id, timeZone, openingHours); // fail fast at startup on a bad zone or period
        }

        public OpeningHours toOpeningHours() {
            return buildOpeningHours(id, timeZone, openingHours);
        }

        private static OpeningHours buildOpeningHours(String id, String timeZone, Map<DayOfWeek, String> openingHours) {
            if (openingHours.isEmpty()) {
                return OpeningHours.ALWAYS;
            }
            if (timeZone == null || timeZone.isBlank()) {
                throw new IllegalArgumentException("Provider %s: time-zone is required with opening-hours".formatted(id));
            }
            Map<DayOfWeek, Period> periods = new EnumMap<>(DayOfWeek.class);
            openingHours.forEach((day, range) -> periods.put(day, Period.parse(range)));
            return OpeningHours.of(ZoneId.of(timeZone), periods);
        }
    }

    public record ServiceConfig(@NotBlank String code, @NotBlank String name, @Min(1) int durationMinutes) {
    }
}
