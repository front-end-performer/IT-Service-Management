package de.challenge.appointments.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brings the database catalog in line with {@link CatalogProperties} on every start:
 * new providers/services are inserted, names, capacities and durations are updated.
 * Entries removed from the configuration are kept, because appointments may reference them.
 */
@Component
class CatalogInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogInitializer.class);

    private final CatalogProperties properties;
    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;

    CatalogInitializer(CatalogProperties properties, ProviderRepository providers, ServiceOfferingRepository services) {
        this.properties = properties;
        this.providers = providers;
        this.services = services;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (var config : properties.providers()) {
            Provider provider = providers.findById(config.id())
                    .map(existing -> {
                        existing.update(config.name(), config.capacity());
                        return existing;
                    })
                    .orElseGet(() -> providers.save(new Provider(config.id(), config.name(), config.capacity())));

            for (var serviceConfig : config.services()) {
                services.findByProviderIdAndCode(provider.getId(), serviceConfig.code())
                        .ifPresentOrElse(
                                existing -> existing.update(serviceConfig.name(), serviceConfig.durationMinutes()),
                                () -> services.save(new ServiceOffering(provider, serviceConfig.code(),
                                        serviceConfig.name(), serviceConfig.durationMinutes())));
            }
            log.info("Catalog: provider {} (capacity {}) with {} services",
                    provider.getId(), provider.getCapacity(), config.services().size());
        }
    }
}
