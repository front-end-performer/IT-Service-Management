package de.challenge.appointments.imports;

/** Outcome of an import. Chunks commit one by one, so a FAILED import keeps the rows of its committed chunks. */
public enum ImportStatus {
    /** Chunks are still being processed. */
    RUNNING,
    /** Every row was processed: imported, skipped as duplicate, or rejected with a reason. */
    COMPLETED,
    /** Aborted by an unexpected error; {@code failureMessage} says where. Re-importing the file is safe. */
    FAILED
}
