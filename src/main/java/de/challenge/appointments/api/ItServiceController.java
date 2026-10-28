package de.challenge.appointments.api;

import de.challenge.appointments.api.Dtos.ItServiceDto;
import de.challenge.appointments.api.Dtos.RepairServiceDto;
import de.challenge.appointments.catalog.CatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Providers ("IT services" in the API) and the services they offer. */
@RestController
@RequestMapping("/it-services")
class ItServiceController {

    private final CatalogService catalog;

    ItServiceController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<ItServiceDto> getITServices() {
        return catalog.providers().stream().map(ItServiceDto::of).toList();
    }

    @GetMapping("/{serviceId}")
    ItServiceDto getITService(@PathVariable String serviceId) {
        return ItServiceDto.of(catalog.provider(serviceId));
    }

    @GetMapping("/{serviceId}/services")
    List<RepairServiceDto> getRepairServices(@PathVariable String serviceId) {
        return catalog.services(serviceId).stream().map(RepairServiceDto::of).toList();
    }
}
