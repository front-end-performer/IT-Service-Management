package de.challenge.appointments.appointment;

import de.challenge.appointments.catalog.Provider;
import de.challenge.appointments.catalog.ProviderRepository;
import de.challenge.appointments.catalog.RepairService;
import de.challenge.appointments.catalog.RepairServiceRepository;
import de.challenge.appointments.common.ConflictException;
import de.challenge.appointments.common.InvalidRequestException;
import de.challenge.appointments.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final ProviderRepository providers;
    private final RepairServiceRepository services;
    private final AppointmentScheduler scheduler;
    private final Clock clock;

    BookingService(ProviderRepository providers, RepairServiceRepository services, AppointmentScheduler scheduler,
                   Clock clock) {
        this.providers = providers;
        this.services = services;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * Books an appointment. Availability is re-checked while holding the provider lock, so
     * concurrent bookings for the same provider cannot overbook it.
     */
    @Transactional
    public Appointment book(String providerId, String serviceCode, String customerNumber, Instant start) {
        Provider provider = providers.findByIdForUpdate(providerId)
                .orElseThrow(() -> new NotFoundException("IT service provider '%s' not found".formatted(providerId)));
        RepairService service = services.findByProviderIdAndCode(provider.getId(), serviceCode)
                .orElseThrow(() -> new NotFoundException(
                        "Service '%s' is not offered by '%s'".formatted(serviceCode, providerId)));

        // Only new bookings must lie in the future; imported legacy appointments may be historic.
        if (start.isBefore(clock.instant())) {
            throw new InvalidRequestException("Start %s is in the past".formatted(start));
        }

        var result = scheduler.schedule(service, customerNumber, start, AppointmentSource.BOOKED, null);
        return switch (result.outcome()) {
            case SCHEDULED -> result.appointment();
            case OFF_GRID -> throw new InvalidRequestException(
                    "Start %s is not on the 15-minute grid".formatted(start));
            case DUPLICATE -> throw new ConflictException(
                    "Customer %s already has %s at %s".formatted(customerNumber, serviceCode, start));
            case CAPACITY_EXCEEDED -> throw new ConflictException(
                    "No capacity left for %s at %s".formatted(serviceCode, start));
        };
    }
}
