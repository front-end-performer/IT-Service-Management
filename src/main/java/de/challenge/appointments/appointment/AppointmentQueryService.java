package de.challenge.appointments.appointment;

import de.challenge.appointments.catalog.ProviderRepository;
import de.challenge.appointments.common.InvalidRequestException;
import de.challenge.appointments.common.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private final ProviderRepository providers;
    private final AppointmentRepository appointments;

    AppointmentQueryService(ProviderRepository providers, AppointmentRepository appointments) {
        this.providers = providers;
        this.appointments = appointments;
    }

    /** One page of the provider's appointments matching the filter, ordered by start. */
    @Transactional(readOnly = true)
    public Page<Appointment> list(String providerId, Filter filter, int page, int size) {
        requireProvider(providerId);
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
        return appointments.findAll(Specification.allOf(specs), PageRequest.of(page, size, Sort.by("start", "id")));
    }

    @Transactional(readOnly = true)
    public Appointment get(String providerId, String appointmentId) {
        requireProvider(providerId);
        return appointments.findByIdAndProviderId(appointmentId, providerId)
                .orElseThrow(() -> new NotFoundException("Appointment '%s' not found".formatted(appointmentId)));
    }

    private void requireProvider(String providerId) {
        if (!providers.existsById(providerId)) {
            throw new NotFoundException("IT service provider '%s' not found".formatted(providerId));
        }
    }
}
