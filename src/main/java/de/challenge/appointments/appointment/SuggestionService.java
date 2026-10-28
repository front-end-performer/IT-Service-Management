package de.challenge.appointments.appointment;

import de.challenge.appointments.catalog.Provider;
import de.challenge.appointments.catalog.ProviderRepository;
import de.challenge.appointments.catalog.RepairService;
import de.challenge.appointments.catalog.RepairServiceRepository;
import de.challenge.appointments.common.InvalidRequestException;
import de.challenge.appointments.common.NotFoundException;
import de.challenge.appointments.scheduling.SlotFinder;
import de.challenge.appointments.scheduling.TimeSlot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only availability search. Suggestions are a snapshot; {@link BookingService} checks again
 * under lock, so a slot taken in the meantime results in HTTP 409 rather than an overbooking.
 */
@Service
public class SuggestionService {

    static final Duration MAX_RANGE = Duration.ofDays(31);

    private final ProviderRepository providers;
    private final RepairServiceRepository services;
    private final AppointmentRepository appointments;

    SuggestionService(ProviderRepository providers, RepairServiceRepository services,
                      AppointmentRepository appointments) {
        this.providers = providers;
        this.services = services;
        this.appointments = appointments;
    }

    public record Suggestion(TimeSlot slot, Provider provider, RepairService service) {
    }

    @Transactional(readOnly = true)
    public List<Suggestion> suggest(String providerId, String serviceCode, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new InvalidRequestException("'from' must be before 'to'");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("Search range must not exceed %d days".formatted(MAX_RANGE.toDays()));
        }
        Provider provider = providers.findById(providerId)
                .orElseThrow(() -> new NotFoundException("IT service provider '%s' not found".formatted(providerId)));
        RepairService service = services.findByProviderIdAndCode(providerId, serviceCode)
                .orElseThrow(() -> new NotFoundException(
                        "Service '%s' is not offered by '%s'".formatted(serviceCode, providerId)));

        List<TimeSlot> booked = appointments.findOverlapping(providerId, from, to).stream()
                .map(Appointment::slot).toList();
        return SlotFinder.findFreeSlots(from, to, service.getDuration(), booked, provider.getCapacity()).stream()
                .map(slot -> new Suggestion(slot, provider, service))
                .toList();
    }
}
