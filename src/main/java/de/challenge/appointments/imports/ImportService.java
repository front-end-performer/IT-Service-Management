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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports legacy appointments for exactly one provider, chosen by the caller (never derived
 * from the file name). Rows are checked in file order with the same rules as bookings; rows
 * that fail are stored with their reason, all other rows are imported.
 *
 * <p>Rows are processed in chunks, each in its own transaction under the provider lock, so a
 * large file never blocks bookings of that provider for longer than one chunk.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;
    private final AppointmentScheduler scheduler;
    private final ImportBatchRepository batches;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final int chunkSize;

    ImportService(ProviderRepository providers, ServiceOfferingRepository services, AppointmentScheduler scheduler,
                  ImportBatchRepository batches, Clock clock, TransactionTemplate transactions,
                  @Value("${appointments.import-chunk-size}") int chunkSize) {
        this.providers = providers;
        this.services = services;
        this.scheduler = scheduler;
        this.batches = batches;
        this.clock = clock;
        this.transactions = transactions;
        this.chunkSize = chunkSize;
    }

    public ImportBatch importCsv(String providerId, String fileName, Reader csv) throws IOException {
        if (!providers.existsById(providerId)) {
            throw new NotFoundException("IT service provider '%s' not found".formatted(providerId));
        }
        // File-level errors (header, structure) are raised before anything is written.
        List<Row> rows = CsvAppointmentParser.parse(csv);

        // Committed first so appointments of every chunk can reference the batch.
        String batchId = transactions.execute(status ->
                batches.save(new ImportBatch(providerId, fileName, clock.instant())).getId());
        for (int from = 0; from < rows.size(); from += chunkSize) {
            List<Row> chunk = rows.subList(from, Math.min(from + chunkSize, rows.size()));
            transactions.executeWithoutResult(status -> importChunk(providerId, batchId, chunk));
        }

        ImportBatch batch = transactions.execute(status -> batches.findByIdAndProviderId(batchId, providerId).orElseThrow());
        log.info("Import {} into {}: {} rows, {} imported, {} duplicates, {} rejected", batch.getId(),
                providerId, batch.getTotalRows(), batch.getImported(), batch.getDuplicates(), batch.getRejected());
        return batch;
    }

    /** One transaction: takes the provider lock, checks and inserts the rows in file order, commits. */
    private void importChunk(String providerId, String batchId, List<Row> chunk) {
        Provider provider = providers.findByIdForUpdate(providerId).orElseThrow();
        Map<String, ServiceOffering> servicesByCode = services.findByProviderIdOrderByCode(provider.getId()).stream()
                .collect(Collectors.toMap(ServiceOffering::getCode, Function.identity(), (a, b) -> a, TreeMap::new));
        ImportBatch batch = batches.findById(batchId).orElseThrow();
        for (Row row : chunk) {
            switch (row) {
                case InvalidRow invalid -> batch.recordNotImported(invalid.lineNumber(), invalid.rawLine(),
                        invalid.reason(), invalid.message());
                case ValidRow valid -> importRow(batch, valid, servicesByCode);
            }
        }
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
