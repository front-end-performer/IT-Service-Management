package de.challenge.appointments.common;

/** A requested provider, service, appointment or import batch does not exist (HTTP 404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
