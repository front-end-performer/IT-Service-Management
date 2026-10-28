package de.challenge.appointments.api;

import de.challenge.appointments.api.Dtos.ItServiceDto;
import de.challenge.appointments.api.Dtos.RepairServiceDto;
import de.challenge.appointments.catalog.ProviderRepository;
import de.challenge.appointments.catalog.ServiceOfferingRepository;
import de.challenge.appointments.common.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Providers ("IT services" in the API) and the services they offer. */
@RestController
@RequestMapping("/it-services")
class ItServiceController {

    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;

    ItServiceController(ProviderRepository providers, ServiceOfferingRepository services) {
        this.providers = providers;
        this.services = services;
    }

    @GetMapping
    List<ItServiceDto> getITServices() {
        return providers.findAll(Sort.by("name")).stream().map(ItServiceDto::of).toList();
    }

    @GetMapping("/{serviceId}")
    ItServiceDto getITService(@PathVariable String serviceId) {
        return providers.findById(serviceId).map(ItServiceDto::of)
                .orElseThrow(() -> notFound(serviceId));
    }

    @GetMapping("/{serviceId}/services")
    List<RepairServiceDto> getRepairServices(@PathVariable String serviceId) {
        if (!providers.existsById(serviceId)) {
            throw notFound(serviceId);
        }
        return services.findByProviderIdOrderByCode(serviceId).stream().map(RepairServiceDto::of).toList();
    }

    private static NotFoundException notFound(String serviceId) {
        return new NotFoundException("IT service provider '%s' not found".formatted(serviceId));
    }
}
