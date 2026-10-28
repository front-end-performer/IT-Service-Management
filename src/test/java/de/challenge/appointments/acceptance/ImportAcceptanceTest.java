package de.challenge.appointments.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * I1–I9: CSV import through {@code POST /it-services/{id}/appointment-imports}; the provider comes
 * from the path. Policy (ADR 0005): partial import in file order, every rejected row reported.
 */
class ImportAcceptanceTest extends AcceptanceTest {

    /** The example from the task text. */
    private static final String TASK_SAMPLE = """
            KdnNr,APP_DATE,SERVICE
            10001,2026-11-02T08:00Z,DIA
            10002,2026-11-02T08:30Z,DIA
            10003,2026-11-02T09:00Z,LAP
            """;

    @Test
    @DisplayName("I1 The task's sample file for Müller: 3 imported, ending 08:30, 09:00, 11:00")
    void I1_taskSampleForMueller() throws Exception {
        importCsv(MUELLER, TASK_SAMPLE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.rejected").value(0));

        appointments(MUELLER).andExpect(jsonPath("$[*].end").value(contains(at("08:30"), at("09:00"), at("11:00"))));
    }

    @Test
    @DisplayName("I2 Unknown service code (VIR for Müller): row rejected, report shows line and reason")
    void I2_unknownServiceCode() throws Exception {
        importCsv(MUELLER, "KdnNr,APP_DATE,SERVICE\n20001,2026-11-02T08:00Z,VIR\n")
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.rejections[0].lineNumber").value(2))
                .andExpect(jsonPath("$.rejections[0].rawLine").value("20001,2026-11-02T08:00Z,VIR"))
                .andExpect(jsonPath("$.rejections[0].reason").value("UNKNOWN_SERVICE_CODE"));
    }

    @Test
    @DisplayName("I3 Invalid records (bad date, missing value, wrong column count): rejected with line and reason")
    void I3_invalidRecords() throws Exception {
        importCsv(MUELLER, """
                KdnNr,APP_DATE,SERVICE
                1,02.11.2026 08:00,DIA
                2,,DIA
                3,2026-11-02T08:00Z
                4,2026-11-02T08:00Z,DIA
                """)
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejections[*].lineNumber").value(contains(2, 3, 4)))
                .andExpect(jsonPath("$.rejections[*].reason").value(contains("INVALID_DATE", "MISSING_FIELD", "MALFORMED_ROW")));
    }

    @Test
    @DisplayName("I4 A start off the grid (08:10Z) is rejected")
    void I4_offGrid() throws Exception {
        importCsv(MUELLER, "KdnNr,APP_DATE,SERVICE\n1,2026-11-02T08:10Z,DIA\n")
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.rejections[0].reason").value("OFF_GRID"));
    }

    @Test
    @DisplayName("I5 The third parallel row within a file is rejected for capacity, the other rows are imported")
    void I5_capacityWithinFile() throws Exception {
        importCsv(MUELLER, """
                KdnNr,APP_DATE,SERVICE
                1,2026-11-02T08:00Z,DIA
                2,2026-11-02T08:00Z,DIA
                3,2026-11-02T08:00Z,DIA
                4,2026-11-02T09:00Z,DIA
                """)
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.rejections[0].lineNumber").value(4))
                .andExpect(jsonPath("$.rejections[0].reason").value("CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("I6 A row that exceeds capacity together with existing bookings is rejected")
    void I6_capacityWithExistingBookings() throws Exception {
        booked(MUELLER, "LAP", "I6-1", at("08:00"));
        booked(MUELLER, "LAP", "I6-2", at("08:00"));

        importCsv(MUELLER, "KdnNr,APP_DATE,SERVICE\n3,2026-11-02T09:00Z,DIA\n")
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.rejections[0].reason").value("CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("I7 The task's sample file for Schneider: DIA rows imported with 15 minutes, LAP rejected as unknown")
    void I7_taskSampleForSchneider() throws Exception {
        importCsv(SCHNEIDER, TASK_SAMPLE)
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.rejections[0].lineNumber").value(4))
                .andExpect(jsonPath("$.rejections[0].reason").value("UNKNOWN_SERVICE_CODE"));

        appointments(SCHNEIDER).andExpect(jsonPath("$[*].end").value(contains(at("08:15"), at("08:45"))));
    }

    @Test
    @DisplayName("I8 Import for an unknown provider: error, nothing imported")
    void I8_unknownProvider() throws Exception {
        importCsv("UNKNOWN", TASK_SAMPLE).andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from appointment", Integer.class)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from import_batch", Integer.class)).isZero();
    }

    @Test
    @DisplayName("I9 A non-numeric customer number (ABC-7) is accepted")
    void I9_nonNumericCustomer() throws Exception {
        importCsv(MUELLER, "KdnNr,APP_DATE,SERVICE\nABC-7,2026-11-02T08:00Z,DIA\n").andExpect(jsonPath("$.imported").value(1));

        appointments(MUELLER).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].customerNumber").value("ABC-7"));
    }
}
