package de.challenge.appointments.api;

import de.challenge.appointments.api.Dtos.AppointmentDto;
import de.challenge.appointments.api.Dtos.AppointmentRequest;
import de.challenge.appointments.api.Dtos.AppointmentSuggestionDto;
import de.challenge.appointments.appointment.AppointmentQueryService;
import de.challenge.appointments.appointment.BookingService;
import de.challenge.appointments.appointment.SuggestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    static final int DEFAULT_PAGE_SIZE = 100;
    static final int MAX_PAGE_SIZE = 500;
    static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final AppointmentQueryService queries;
    private final BookingService booking;
    private final SuggestionService suggestions;

    AppointmentController(AppointmentQueryService queries, BookingService booking, SuggestionService suggestions) {
        this.queries = queries;
        this.booking = booking;
        this.suggestions = suggestions;
    }

    /**
     * Paged (extension): the body stays the plain array of the spec, the total is in {@code X-Total-Count}.
     * Without paging parameters the first {@value #DEFAULT_PAGE_SIZE} appointments are returned.
     */
    @GetMapping("/appointments")
    ResponseEntity<List<AppointmentDto>> getAppointments(
            @PathVariable String serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String repairServiceId,
            @RequestParam(required = false) String customerNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        var filter = new AppointmentQueryService.Filter(instant(from), instant(to), blankToNull(repairServiceId),
                blankToNull(customerNumber));
        var result = queries.list(serviceId, filter, page, size);
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(result.getTotalElements()))
                .body(result.map(AppointmentDto::of).getContent());
    }

    @PostMapping("/appointments")
    ResponseEntity<AppointmentDto> createAppointment(@PathVariable String serviceId,
                                                     @Valid @RequestBody AppointmentRequest request) {
        var appointment = booking.book(serviceId, request.repairServiceId(), request.customerNumber().trim(),
                request.start().toInstant());
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
