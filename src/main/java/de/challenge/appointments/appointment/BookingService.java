package de.challenge.appointments.appointment;

import de.challenge.appointments.catalog.CatalogService;
import de.challenge.appointments.catalog.Provider;
import de.challenge.appointments.catalog.ServiceOffering;
import de.challenge.appointments.common.ConflictException;
import de.challenge.appointments.common.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final CatalogService catalog;
    private final AppointmentScheduler scheduler;
    private final Clock clock;

    BookingService(CatalogService catalog, AppointmentScheduler scheduler, Clock clock) {
        this.catalog = catalog;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * Books an appointment. Availability is re-checked while holding the provider lock, so
     * concurrent bookings for the same provider cannot overbook it.
     */
    @Transactional
    public Appointment book(String providerId, String serviceCode, String customerNumber, Instant start) {
        Provider provider = catalog.lockProvider(providerId);
        ServiceOffering service = catalog.service(providerId, serviceCode);

        // Only new bookings must lie in the future; imported legacy appointments may be historic.
        if (start.isBefore(clock.instant())) {
            throw new InvalidRequestException("Start %s is in the past".formatted(start));
        }

        var result = scheduler.schedule(service, customerNumber, start, AppointmentSource.BOOKED, null);
        return switch (result.outcome()) {
            case SCHEDULED -> result.appointment();
            case OFF_GRID -> throw new InvalidRequestException(
                    "Start %s is not on the 15-minute grid".formatted(start));
            case OUTSIDE_OPENING_HOURS -> throw new InvalidRequestException(
                    "%s at %s is outside the opening hours of %s (%s)".formatted(serviceCode, start, providerId,
                            provider.getOpeningHours()));
            case DUPLICATE -> throw new ConflictException(
                    "Customer %s already has %s at %s".formatted(customerNumber, serviceCode, start));
            case CAPACITY_EXCEEDED -> throw new ConflictException(
                    "No capacity left for %s at %s".formatted(serviceCode, start));
        };
    }
}
