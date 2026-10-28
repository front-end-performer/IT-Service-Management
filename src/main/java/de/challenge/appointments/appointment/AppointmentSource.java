package de.challenge.appointments.appointment;

public enum AppointmentSource {
    /** Booked through the REST API / frontend. */
    BOOKED,
    /** Taken over from the legacy system via CSV import. */
    IMPORTED
}
