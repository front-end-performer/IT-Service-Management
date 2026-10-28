package de.challenge.appointments.imports;

import de.challenge.appointments.appointment.Appointment;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parses the legacy format {@code KdnNr,APP_DATE,SERVICE}. Syntax problems of single rows are
 * reported per row; only a missing/foreign header makes the whole file unusable.
 */
public final class CsvAppointmentParser {

    static final String CUSTOMER = "KdnNr";
    static final String START = "APP_DATE";
    static final String SERVICE = "SERVICE";
    private static final List<String> HEADER = List.of(CUSTOMER, START, SERVICE);

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setAllowMissingColumnNames(false)
            .get();

    private CsvAppointmentParser() {
    }

    public sealed interface Row permits ValidRow, InvalidRow {
        long lineNumber();

        String rawLine();
    }

    public record ValidRow(long lineNumber, String rawLine, String customerNumber, Instant start, String serviceCode)
            implements Row {
    }

    public record InvalidRow(long lineNumber, String rawLine, RejectionReason reason, String message)
            implements Row {
    }

    public static List<Row> parse(Reader reader) throws IOException {
        try {
            return parseRows(reader);
        } catch (IllegalArgumentException | IllegalStateException | java.io.UncheckedIOException e) {
            // Duplicate header names, unterminated quotes and similar structural errors.
            throw new InvalidCsvException("Unreadable CSV: " + e.getMessage());
        }
    }

    private static List<Row> parseRows(Reader reader) throws IOException {
        try (CSVParser parser = CSVParser.parse(new BomSkippingReader(reader), FORMAT)) {
            List<String> header = parser.getHeaderNames();
            if (!header.containsAll(HEADER)) {
                throw new InvalidCsvException("Expected header %s but found %s"
                        .formatted(String.join(",", HEADER), String.join(",", header)));
            }
            List<Row> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                rows.add(toRow(record, header.size()));
            }
            return rows;
        }
    }

    private static Row toRow(CSVRecord record, int columns) {
        long line = record.getParser().getCurrentLineNumber();
        String raw = String.join(",", record.values());
        if (record.size() != columns) {
            return new InvalidRow(line, raw, RejectionReason.MALFORMED_ROW,
                    "Expected %d columns but found %d".formatted(columns, record.size()));
        }
        String customer = record.get(CUSTOMER);
        String date = record.get(START);
        String service = record.get(SERVICE);
        for (String column : HEADER) {
            if (record.get(column).isEmpty()) {
                return new InvalidRow(line, raw, RejectionReason.MISSING_FIELD, column + " is empty");
            }
        }
        if (customer.length() > Appointment.MAX_CUSTOMER_NUMBER_LENGTH) {
            return new InvalidRow(line, raw, RejectionReason.INVALID_CUSTOMER_NUMBER,
                    "KdnNr is longer than %d characters".formatted(Appointment.MAX_CUSTOMER_NUMBER_LENGTH));
        }
        try {
            Instant start = OffsetDateTime.parse(date).toInstant();
            return new ValidRow(line, raw, customer, start, service);
        } catch (DateTimeParseException e) {
            return new InvalidRow(line, raw, RejectionReason.INVALID_DATE,
                    "'%s' is not an ISO-8601 date-time with offset".formatted(date));
        }
    }

    /** Excel and other Windows tools prepend a UTF-8 BOM that would otherwise end up in the first header name. */
    private static final class BomSkippingReader extends java.io.FilterReader {

        private boolean first = true;

        BomSkippingReader(Reader in) {
            super(in);
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (first && n > 0) {
                first = false;
                if (buffer[offset] == '﻿') {
                    System.arraycopy(buffer, offset + 1, buffer, offset, n - 1);
                    return n == 1 ? read(buffer, offset, length) : n - 1;
                }
            }
            return n;
        }

        @Override
        public int read() throws IOException {
            char[] one = new char[1];
            return read(one, 0, 1) == -1 ? -1 : one[0];
        }
    }
}
