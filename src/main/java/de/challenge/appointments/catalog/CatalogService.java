package de.challenge.appointments.catalog;

import de.challenge.appointments.common.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for reading providers and their services. Owns the "not found" rules, so the
 * REST layer, booking, suggestions, queries and imports all answer an unknown provider or service
 * the same way.
 */
@Service
public class CatalogService {

    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;

    CatalogService(ProviderRepository providers, ServiceOfferingRepository services) {
        this.providers = providers;
        this.services = services;
    }

    @Transactional(readOnly = true)
    public List<Provider> providers() {
        return providers.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Provider provider(String providerId) {
        return providers.findById(providerId).orElseThrow(() -> providerNotFound(providerId));
    }

    /** Throws {@link NotFoundException} unless the provider exists. */
    @Transactional(readOnly = true)
    public void requireProvider(String providerId) {
        if (!providers.existsById(providerId)) {
            throw providerNotFound(providerId);
        }
    }

    /**
     * Loads the provider with its row lock ({@code SELECT … FOR UPDATE}); every change to a
     * provider's schedule starts here. Must run inside the caller's transaction, which holds the lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Provider lockProvider(String providerId) {
        return providers.findByIdForUpdate(providerId).orElseThrow(() -> providerNotFound(providerId));
    }

    /** The provider's services, ordered by code. */
    @Transactional(readOnly = true)
    public List<ServiceOffering> services(String providerId) {
        requireProvider(providerId);
        return services.findByProviderIdOrderByCode(providerId);
    }

    /** A service is always resolved together with its provider: the code is unique per provider only. */
    @Transactional(readOnly = true)
    public ServiceOffering service(String providerId, String code) {
        requireProvider(providerId);
        return services.findByProviderIdAndCode(providerId, code).orElseThrow(() ->
                new NotFoundException("Service '%s' is not offered by '%s'".formatted(code, providerId)));
    }

    private static NotFoundException providerNotFound(String providerId) {
        return new NotFoundException("IT service provider '%s' not found".formatted(providerId));
    }
}
