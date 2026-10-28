package de.challenge.appointments.imports;

/** Why a CSV row was not imported. Stored with every rejected row for traceability. */
public enum RejectionReason {
    /** Wrong number of columns. */
    MALFORMED_ROW,
    MISSING_FIELD,
    /** APP_DATE is not an ISO-8601 date-time with offset, e.g. 2026-11-02T08:00Z. */
    INVALID_DATE,
    /** Start is not on the 15-minute grid. */
    OFF_GRID,
    /** The appointment would not lie completely within the provider's opening hours. */
    OUTSIDE_OPENING_HOURS,
    /** SERVICE is not offered by the provider the file is imported for. */
    UNKNOWN_SERVICE_CODE,
    /** The appointment would exceed the provider's capacity. */
    CAPACITY_EXCEEDED,
    /** Same customer, service and start already exist (e.g. file imported twice); skipped, not an error. */
    DUPLICATE
}
