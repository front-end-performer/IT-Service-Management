package de.challenge.appointments.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Providers and their services as configured under {@code catalog.providers} in application.yml. */
@Validated
@ConfigurationProperties("catalog")
public record CatalogProperties(List<@Valid ProviderConfig> providers) {

    public CatalogProperties {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public record ProviderConfig(@NotBlank String id, @NotBlank String name, @Min(1) int capacity,
                                 List<@Valid ServiceConfig> services) {

        public ProviderConfig {
            services = services == null ? List.of() : List.copyOf(services);
        }
    }

    public record ServiceConfig(@NotBlank String code, @NotBlank String name, @Min(1) int durationMinutes) {
    }
}
