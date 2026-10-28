package de.challenge.appointments.common;

/** Syntactically valid request that breaks a business rule, e.g. an off-grid start (HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
