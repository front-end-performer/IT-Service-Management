package de.challenge.appointments.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.challenge.appointments.imports.CsvAppointmentParser.InvalidRow;
import de.challenge.appointments.imports.CsvAppointmentParser.ValidRow;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvAppointmentParserTest {

    private static List<CsvAppointmentParser.Row> parse(String csv) throws Exception {
        return CsvAppointmentParser.parse(new StringReader(csv));
    }

    @Test
    void parsesTheLegacyFormat() throws Exception {
        var rows = parse("KdnNr,APP_DATE,SERVICE\n10001,2026-11-02T08:00Z,DIA\n10002, 2026-11-02T09:30+01:00 ,LAP\n");

        assertThat(rows).containsExactly(
                new ValidRow(2, "10001,2026-11-02T08:00Z,DIA", "10001", Instant.parse("2026-11-02T08:00:00Z"), "DIA"),
                new ValidRow(3, "10002,2026-11-02T09:30+01:00,LAP", "10002", Instant.parse("2026-11-02T08:30:00Z"), "LAP"));
    }

    @Test
    void toleratesByteOrderMarkCrlfAndBlankLines() throws Exception {
        var rows = parse("﻿KdnNr,APP_DATE,SERVICE\r\n\r\n10001,2026-11-02T08:00Z,DIA\r\n");

        assertThat(rows).singleElement().isInstanceOf(ValidRow.class);
    }

    @Test
    void reportsRowLevelProblemsWithoutFailingTheFile() throws Exception {
        var rows = parse("""
                KdnNr,APP_DATE,SERVICE
                1,2026-13-02T08:00Z,DIA
                2,2026-11-02T08:00,DIA
                ,2026-11-02T08:00Z,DIA
                4,2026-11-02T08:00Z,
                5,2026-11-02T08:00Z,DIA,extra
                """);

        assertThat(rows).extracting(row -> ((InvalidRow) row).reason()).containsExactly(
                RejectionReason.INVALID_DATE,
                RejectionReason.INVALID_DATE, // no offset: ambiguous, not silently assumed UTC
                RejectionReason.MISSING_FIELD,
                RejectionReason.MISSING_FIELD,
                RejectionReason.MALFORMED_ROW);
        assertThat(rows).extracting(CsvAppointmentParser.Row::lineNumber).containsExactly(2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void rejectsFilesWithoutTheExpectedHeader() {
        assertThatThrownBy(() -> parse("a,b,c\n1,2,3\n")).isInstanceOf(InvalidCsvException.class);
        assertThatThrownBy(() -> parse("")).isInstanceOf(InvalidCsvException.class);
        assertThatThrownBy(() -> parse("KdnNr,KdnNr,SERVICE\n")).isInstanceOf(InvalidCsvException.class);
    }
}
