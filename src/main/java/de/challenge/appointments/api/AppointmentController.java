package de.challenge.appointments.api;

import de.challenge.appointments.api.Dtos.AppointmentDto;
import de.challenge.appointments.api.Dtos.AppointmentRequest;
import de.challenge.appointments.api.Dtos.AppointmentSuggestionDto;
import de.challenge.appointments.appointment.AppointmentQueryService;
import de.challenge.appointments.appointment.BookingService;
import de.challenge.appointments.appointment.SuggestionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/it-services/{serviceId}")
class AppointmentController {

    private final AppointmentQueryService queries;
    private final BookingService booking;
    private final SuggestionService suggestions;

    AppointmentController(AppointmentQueryService queries, BookingService booking, SuggestionService suggestions) {
        this.queries = queries;
        this.booking = booking;
        this.suggestions = suggestions;
    }

    @GetMapping("/appointments")
    List<AppointmentDto> getAppointments(@PathVariable String serviceId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime to,
                                         @RequestParam(required = false) String repairServiceId,
                                         @RequestParam(required = false) String customerNumber) {
        var filter = new AppointmentQueryService.Filter(instant(from), instant(to), blankToNull(repairServiceId),
                blankToNull(customerNumber));
        return queries.list(serviceId, filter).stream().map(AppointmentDto::of).toList();
    }

    @PostMapping("/appointments")
    ResponseEntity<AppointmentDto> createAppointment(@PathVariable String serviceId,
                                                     @Valid @RequestBody AppointmentRequest request) {
        var appointment = booking.book(serviceId, request.repairServiceId(), request.customerNumber().trim(),
                request.start());
        return ResponseEntity.created(URI.create("/it-services/%s/appointments/%s"
                        .formatted(serviceId, appointment.getId())))
                .body(AppointmentDto.of(appointment));
    }

    @GetMapping("/appointments/{appointmentId}")
    AppointmentDto getAppointment(@PathVariable String serviceId, @PathVariable String appointmentId) {
        return AppointmentDto.of(queries.get(serviceId, appointmentId));
    }

    @GetMapping("/appointment-suggestions")
    List<AppointmentSuggestionDto> getAppointmentSuggestions(
            @PathVariable String serviceId,
            @RequestParam String repairServiceId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime to) {
        return suggestions.suggest(serviceId, repairServiceId, from.toInstant(), to.toInstant()).stream()
                .map(AppointmentSuggestionDto::of).toList();
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
