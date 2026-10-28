package de.challenge.appointments.appointment;

import de.challenge.appointments.catalog.ServiceOffering;
import de.challenge.appointments.scheduling.CapacityCalculator;
import de.challenge.appointments.scheduling.TimeGrid;
import de.challenge.appointments.scheduling.TimeSlot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place where appointments are written. Shared by booking and CSV import so that
 * both obey exactly the same grid, duplicate and capacity rules.
 *
 * <p>Callers must hold the provider lock ({@code CatalogService#lockProvider}) in the
 * surrounding transaction; otherwise two transactions could both see free capacity.
 */
@Component
public class AppointmentScheduler {

    public enum Outcome { SCHEDULED, OFF_GRID, OUTSIDE_OPENING_HOURS, DUPLICATE, CAPACITY_EXCEEDED }

    public record Result(Outcome outcome, Appointment appointment) {
    }

    private final AppointmentRepository appointments;
    private final Clock clock;

    AppointmentScheduler(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result schedule(ServiceOffering service, String customerNumber, Instant start,
                           AppointmentSource source, String importBatchId) {
        if (!TimeGrid.isAligned(start)) {
            return new Result(Outcome.OFF_GRID, null);
        }
        TimeSlot candidate = TimeSlot.of(start, service.getDuration());
        if (!service.getProvider().getOpeningHours().contains(candidate)) {
            return new Result(Outcome.OUTSIDE_OPENING_HOURS, null);
        }
        String providerId = service.getProvider().getId();
        if (appointments.existsByProviderIdAndServiceOfferingIdAndCustomerNumberAndStart(
                providerId, service.getId(), customerNumber, start)) {
            return new Result(Outcome.DUPLICATE, null);
        }
        List<TimeSlot> booked = appointments.findOverlapping(providerId, candidate.start(), candidate.end())
                .stream().map(Appointment::slot).toList();
        if (!CapacityCalculator.fits(booked, candidate, service.getProvider().getCapacity())) {
            return new Result(Outcome.CAPACITY_EXCEEDED, null);
        }
        Appointment appointment = new Appointment(service, customerNumber, start, source, importBatchId,
                clock.instant());
        return new Result(Outcome.SCHEDULED, appointments.save(appointment));
    }
}
