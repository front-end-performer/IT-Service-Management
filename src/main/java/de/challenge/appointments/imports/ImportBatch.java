package de.challenge.appointments.imports;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One CSV upload: counters plus every row that was not imported, with the reason. */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    private String id;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int imported;

    @Column(nullable = false)
    private int duplicates;

    @Column(nullable = false)
    private int rejected;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    @Column(name = "failure_message")
    private String failureMessage;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "batch_id", nullable = false)
    @OrderBy("lineNumber")
    private List<ImportRejection> rejections = new ArrayList<>();

    protected ImportBatch() {
    }

    ImportBatch(String providerId, String fileName, Instant importedAt) {
        this.id = UUID.randomUUID().toString();
        this.providerId = providerId;
        this.fileName = fileName;
        this.importedAt = importedAt;
        this.status = ImportStatus.RUNNING;
    }

    void complete() {
        status = ImportStatus.COMPLETED;
    }

    void fail(String message) {
        status = ImportStatus.FAILED;
        failureMessage = message.length() > 500 ? message.substring(0, 500) : message;
    }

    void recordImported() {
        totalRows++;
        imported++;
    }

    void recordNotImported(long lineNumber, String rawLine, RejectionReason reason, String message) {
        totalRows++;
        if (reason == RejectionReason.DUPLICATE) {
            duplicates++;
        } else {
            rejected++;
        }
        rejections.add(new ImportRejection(lineNumber, rawLine, reason, message));
    }

    public String getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getFileName() {
        return fileName;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getImported() {
        return imported;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public int getRejected() {
        return rejected;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public List<ImportRejection> getRejections() {
        return rejections;
    }
}
