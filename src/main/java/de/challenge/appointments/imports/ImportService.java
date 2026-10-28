package de.challenge.appointments.imports;

import de.challenge.appointments.appointment.AppointmentScheduler;
import de.challenge.appointments.appointment.AppointmentSource;
import de.challenge.appointments.catalog.Provider;
import de.challenge.appointments.catalog.ProviderRepository;
import de.challenge.appointments.catalog.ServiceOffering;
import de.challenge.appointments.catalog.ServiceOfferingRepository;
import de.challenge.appointments.common.NotFoundException;
import de.challenge.appointments.imports.CsvAppointmentParser.InvalidRow;
import de.challenge.appointments.imports.CsvAppointmentParser.Row;
import de.challenge.appointments.imports.CsvAppointmentParser.ValidRow;
import java.io.IOException;
import java.io.Reader;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports legacy appointments for exactly one provider, chosen by the caller (never derived
 * from the file name). Rows are checked in file order with the same rules as bookings; rows
 * that fail are stored with their reason, all other rows are imported.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;
    private final AppointmentScheduler scheduler;
    private final ImportBatchRepository batches;
    private final Clock clock;

    ImportService(ProviderRepository providers, ServiceOfferingRepository services, AppointmentScheduler scheduler,
                  ImportBatchRepository batches, Clock clock) {
        this.providers = providers;
        this.services = services;
        this.scheduler = scheduler;
        this.batches = batches;
        this.clock = clock;
    }

    @Transactional
    public ImportBatch importCsv(String providerId, String fileName, Reader csv) throws IOException {
        Provider provider = providers.findByIdForUpdate(providerId)
                .orElseThrow(() -> new NotFoundException("IT service provider '%s' not found".formatted(providerId)));
        List<Row> rows = CsvAppointmentParser.parse(csv);
        Map<String, ServiceOffering> servicesByCode = services.findByProviderIdOrderByCode(provider.getId()).stream()
                .collect(Collectors.toMap(ServiceOffering::getCode, Function.identity(), (a, b) -> a, TreeMap::new));

        // Saved first so appointments can reference the batch.
        ImportBatch batch = batches.save(new ImportBatch(provider.getId(), fileName, clock.instant()));
        for (Row row : rows) {
            switch (row) {
                case InvalidRow invalid -> batch.recordNotImported(invalid.lineNumber(), invalid.rawLine(),
                        invalid.reason(), invalid.message());
                case ValidRow valid -> importRow(batch, valid, servicesByCode);
            }
        }
        log.info("Import {} into {}: {} rows, {} imported, {} duplicates, {} rejected", batch.getId(),
                providerId, batch.getTotalRows(), batch.getImported(), batch.getDuplicates(), batch.getRejected());
        return batch;
    }

    private void importRow(ImportBatch batch, ValidRow row, Map<String, ServiceOffering> servicesByCode) {
        ServiceOffering service = servicesByCode.get(row.serviceCode());
        if (service == null) {
            batch.recordNotImported(row.lineNumber(), row.rawLine(), RejectionReason.UNKNOWN_SERVICE_CODE,
                    "Service code '%s' is not offered by %s (offered: %s)".formatted(row.serviceCode(),
                            batch.getProviderId(), String.join(", ", servicesByCode.keySet())));
            return;
        }
        var result = scheduler.schedule(service, row.customerNumber(), row.start(), AppointmentSource.IMPORTED,
                batch.getId());
        switch (result.outcome()) {
            case SCHEDULED -> batch.recordImported();
            case OFF_GRID -> batch.recordNotImported(row.lineNumber(), row.rawLine(), RejectionReason.OFF_GRID,
                    "Start %s is not on the 15-minute grid".formatted(row.start()));
            case OUTSIDE_OPENING_HOURS -> batch.recordNotImported(row.lineNumber(), row.rawLine(),
                    RejectionReason.OUTSIDE_OPENING_HOURS, "Outside the opening hours (%s)"
                            .formatted(service.getProvider().getOpeningHours()));
            case DUPLICATE -> batch.recordNotImported(row.lineNumber(), row.rawLine(), RejectionReason.DUPLICATE,
                    "Appointment already exists");
            case CAPACITY_EXCEEDED -> batch.recordNotImported(row.lineNumber(), row.rawLine(),
                    RejectionReason.CAPACITY_EXCEEDED, "Capacity of %d would be exceeded"
                            .formatted(service.getProvider().getCapacity()));
        }
    }

    @Transactional(readOnly = true)
    public List<ImportBatch> list(String providerId) {
        if (!providers.existsById(providerId)) {
            throw new NotFoundException("IT service provider '%s' not found".formatted(providerId));
        }
        return batches.findByProviderIdOrderByImportedAtDesc(providerId);
    }

    @Transactional(readOnly = true)
    public ImportBatch get(String providerId, String batchId) {
        return batches.findByIdAndProviderId(batchId, providerId)
                .orElseThrow(() -> new NotFoundException("Import '%s' not found".formatted(batchId)));
    }
}
