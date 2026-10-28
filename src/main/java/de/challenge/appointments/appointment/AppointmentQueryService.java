package de.challenge.appointments.appointment;

import de.challenge.appointments.catalog.CatalogService;
import de.challenge.appointments.common.InvalidRequestException;
import de.challenge.appointments.common.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentQueryService {

    /**
     * Optional filters; {@code from}/{@code to} are inclusive and select every appointment
     * that is (partly) running within that period.
     */
    public record Filter(Instant from, Instant to, String serviceCode, String customerNumber) {
    }

    private final CatalogService catalog;
    private final AppointmentRepository appointments;

    AppointmentQueryService(CatalogService catalog, AppointmentRepository appointments) {
        this.catalog = catalog;
        this.appointments = appointments;
    }

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 500;
    private static final Sort BY_START = Sort.by("start", "id");

    /**
     * The provider's appointments matching the filter, ordered by start. Paging is opt-in: without
     * {@code page} and {@code size} all matches are returned, as the spec describes the list.
     */
    @Transactional(readOnly = true)
    public Page<Appointment> list(String providerId, Filter filter, Integer page, Integer size) {
        catalog.requireProvider(providerId);
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        List<Specification<Appointment>> specs = new ArrayList<>();
        specs.add((root, query, cb) -> cb.equal(root.get("provider").get("id"), providerId));
        if (filter.from() != null) {
            specs.add((root, query, cb) -> cb.greaterThan(root.get("end"), filter.from()));
        }
        if (filter.to() != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("start"), filter.to()));
        }
        if (filter.serviceCode() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("serviceOffering").get("code"), filter.serviceCode()));
        }
        if (filter.customerNumber() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("customerNumber"), filter.customerNumber()));
        }
        Pageable pageable = page == null && size == null
                ? Pageable.unpaged(BY_START)
                : PageRequest.of(page == null ? 0 : page, size == null ? DEFAULT_PAGE_SIZE : size, BY_START);
        return appointments.findAll(Specification.allOf(specs), pageable);
    }

    @Transactional(readOnly = true)
    public Appointment get(String providerId, String appointmentId) {
        catalog.requireProvider(providerId);
        return appointments.findByIdAndProviderId(appointmentId, providerId)
                .orElseThrow(() -> new NotFoundException("Appointment '%s' not found".formatted(appointmentId)));
    }
}
