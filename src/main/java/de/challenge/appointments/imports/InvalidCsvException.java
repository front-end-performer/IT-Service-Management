package de.challenge.appointments.imports;

import de.challenge.appointments.common.InvalidRequestException;

/** The file as a whole cannot be processed (empty, wrong header, unreadable). */
public class InvalidCsvException extends InvalidRequestException {

    public InvalidCsvException(String message) {
        super(message);
    }
}
