package de.challenge.appointments.appointment;

import de.challenge.appointments.catalog.Provider;
import de.challenge.appointments.catalog.CatalogService;
import de.challenge.appointments.catalog.ServiceOffering;
import de.challenge.appointments.common.InvalidRequestException;
import de.challenge.appointments.scheduling.SlotFinder;
import de.challenge.appointments.scheduling.TimeSlot;
import java.time.Clock;
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

    private final CatalogService catalog;
    private final AppointmentRepository appointments;
    private final Clock clock;

    SuggestionService(CatalogService catalog, AppointmentRepository appointments, Clock clock) {
        this.catalog = catalog;
        this.appointments = appointments;
        this.clock = clock;
    }

    public record Suggestion(TimeSlot slot, Provider provider, ServiceOffering service) {
    }

    @Transactional(readOnly = true)
    public List<Suggestion> suggest(String providerId, String serviceCode, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new InvalidRequestException("'from' must be before 'to'");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("Search range must not exceed %d days".formatted(MAX_RANGE.toDays()));
        }
        Provider provider = catalog.provider(providerId);
        ServiceOffering service = catalog.service(providerId, serviceCode);

        // Slots in the past are never offered; a range entirely in the past yields no suggestions.
        Instant now = clock.instant();
        Instant earliest = from.isBefore(now) ? now : from;
        if (!earliest.isBefore(to)) {
            return List.of();
        }
        List<TimeSlot> booked = appointments.findOverlapping(providerId, earliest, to).stream()
                .map(Appointment::slot).toList();
        return SlotFinder.findFreeSlots(earliest, to, service.getDuration(), booked, provider.getCapacity(),
                        provider.getOpeningHours()).stream()
                .map(slot -> new Suggestion(slot, provider, service))
                .toList();
    }
}
