package de.challenge.appointments.imports;

/**
 * An import was aborted by an unexpected error after it had been recorded. The import record is
 * marked {@link ImportStatus#FAILED} and stays retrievable under {@link #getImportId()}.
 */
public class ImportFailedException extends RuntimeException {

    private final String importId;
    private final String providerId;

    ImportFailedException(String importId, String providerId, String message, Throwable cause) {
        super(message, cause);
        this.importId = importId;
        this.providerId = providerId;
    }

    public String getImportId() {
        return importId;
    }

    public String getProviderId() {
        return providerId;
    }
}
