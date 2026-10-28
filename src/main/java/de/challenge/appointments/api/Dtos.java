package de.challenge.appointments.api;

import de.challenge.appointments.appointment.Appointment;
import de.challenge.appointments.appointment.AppointmentSource;
import de.challenge.appointments.appointment.SuggestionService.Suggestion;
import de.challenge.appointments.catalog.Provider;
import de.challenge.appointments.catalog.ServiceOffering;
import de.challenge.appointments.imports.ImportBatch;
import de.challenge.appointments.imports.ImportRejection;
import de.challenge.appointments.imports.RejectionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request/response bodies of rest-api-spec.yml. Naming follows the spec: {@code serviceId} is the
 * IT service <em>provider</em>, {@code repairServiceId} the service it offers (its code).
 */
final class Dtos {

    private Dtos() {
    }

    record ItServiceDto(String id, String name, int capacity) {
        static ItServiceDto of(Provider provider) {
            return new ItServiceDto(provider.getId(), provider.getName(), provider.getCapacity());
        }
    }

    record RepairServiceDto(String id, String code, String name, int durationMinutes) {
        static RepairServiceDto of(ServiceOffering service) {
            // The code is unique per provider and stable across restarts, so it doubles as the public id.
            return new RepairServiceDto(service.getCode(), service.getCode(), service.getName(),
                    service.getDurationMinutes());
        }
    }

    /** {@code source} is an addition to the spec so staff can tell booked from imported appointments. */
    record AppointmentDto(String id, String customerNumber, Instant start, Instant end, String serviceId,
                          String serviceName, String repairServiceId, String repairServiceName,
                          AppointmentSource source) {
        static AppointmentDto of(Appointment a) {
            return new AppointmentDto(a.getId(), a.getCustomerNumber(), a.getStart(), a.getEnd(),
                    a.getProvider().getId(), a.getProvider().getName(),
                    a.getServiceOffering().getCode(), a.getServiceOffering().getName(), a.getSource());
        }
    }

    /** {@code start} accepts any offset and optional seconds (e.g. the legacy form 2026-11-02T08:00Z). */
    record AppointmentRequest(@NotBlank @Size(max = 64) String customerNumber,
                              @NotNull OffsetDateTime start,
                              @NotBlank String repairServiceId) {
    }

    record AppointmentSuggestionDto(Instant start, Instant end, String serviceId, String repairServiceId) {
        static AppointmentSuggestionDto of(Suggestion s) {
            return new AppointmentSuggestionDto(s.slot().start(), s.slot().end(), s.provider().getId(),
                    s.service().getCode());
        }
    }

    record ImportReportDto(String id, String serviceId, String fileName, Instant importedAt, int totalRows,
                           int imported, int duplicates, int rejected, List<RejectionDto> rejections) {
        static ImportReportDto of(ImportBatch b, boolean withRejections) {
            return new ImportReportDto(b.getId(), b.getProviderId(), b.getFileName(), b.getImportedAt(),
                    b.getTotalRows(), b.getImported(), b.getDuplicates(), b.getRejected(),
                    withRejections ? b.getRejections().stream().map(RejectionDto::of).toList() : null);
        }
    }

    record RejectionDto(int lineNumber, String rawLine, RejectionReason reason, String message) {
        static RejectionDto of(ImportRejection r) {
            return new RejectionDto(r.getLineNumber(), r.getRawLine(), r.getReason(), r.getMessage());
        }
    }
}
