package de.challenge.appointments.common;

/** The request is valid but clashes with the current state, e.g. capacity exhausted (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
