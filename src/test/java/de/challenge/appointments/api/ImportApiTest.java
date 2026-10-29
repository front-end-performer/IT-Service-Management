package de.challenge.appointments.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.challenge.appointments.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Imports the two legacy files from {@code termine/}. Their names are swapped relative to their
 * content: "Computer-Service-Schneider.csv" holds Müller's services (DIA/LAP/OSI) and vice versa.
 */
@AutoConfigureMockMvc
class ImportApiTest extends IntegrationTest {

    private static final Path MUELLER_CATALOG_FILE = Path.of("termine/Computer-Service-Schneider.csv");
    private static final Path SCHNEIDER_CATALOG_FILE = Path.of("termine/IT-Service-Mueller.csv");

    @Autowired
    private MockMvc mvc;

    private ResultActions upload(String providerId, String fileName, byte[] content) throws Exception {
        return mvc.perform(multipart("/it-services/{id}/appointment-imports", providerId)
                .file(new MockMultipartFile("file", fileName, "text/csv", content)));
    }

    private ResultActions upload(String providerId, Path file) throws Exception {
        return upload(providerId, file.getFileName().toString(), Files.readAllBytes(file));
    }

    @Test
    void importsLegacyFilesIntoTheProviderWhoseServicesTheyContain() throws Exception {
        upload("IT-MUELLER", MUELLER_CATALOG_FILE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(20))
                .andExpect(jsonPath("$.imported").value(20))
                .andExpect(jsonPath("$.rejected").value(0));
        upload("CS-SCHNEIDER", SCHNEIDER_CATALOG_FILE)
                .andExpect(jsonPath("$.imported").value(20))
                .andExpect(jsonPath("$.rejected").value(0));

        mvc.perform(get("/it-services/IT-MUELLER/appointments").param("repairServiceId", "LAP"))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].source").value("IMPORTED"))
                .andExpect(jsonPath("$[0].end").value("2026-11-02T11:00:00Z"));
    }

    @Test
    void importingIntoTheProviderNamedInTheFileNameRejectsForeignServiceCodes() throws Exception {
        upload("CS-SCHNEIDER", MUELLER_CATALOG_FILE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(20))
                .andExpect(jsonPath("$.imported").value(11)) // DIA exists at both providers
                .andExpect(jsonPath("$.rejected").value(9))  // 5x LAP, 4x OSI
                .andExpect(jsonPath("$.rejections[0].lineNumber").value(4))
                .andExpect(jsonPath("$.rejections[0].rawLine").value("10003,2026-11-02T09:00Z,LAP"))
                .andExpect(jsonPath("$.rejections[0].reason").value("UNKNOWN_SERVICE_CODE"));
    }

    @Test
    void reimportingTheSameFileOnlyReportsDuplicates() throws Exception {
        upload("IT-MUELLER", MUELLER_CATALOG_FILE).andExpect(jsonPath("$.imported").value(20));

        upload("IT-MUELLER", MUELLER_CATALOG_FILE)
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.duplicates").value(20))
                .andExpect(jsonPath("$.rejected").value(0));

        mvc.perform(get("/it-services/IT-MUELLER/appointments")).andExpect(jsonPath("$", hasSize(20)));
    }

    @Test
    void reportsEveryInvalidRowWithLineNumberAndReason() throws Exception {
        byte[] csv = getClass().getResourceAsStream("/csv/mixed-errors.csv").readAllBytes();

        String location = upload("IT-MUELLER", "mixed-errors.csv", csv)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("mixed-errors.csv"))
                .andExpect(jsonPath("$.totalRows").value(9))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.rejected").value(6))
                .andExpect(jsonPath("$.rejections[*].lineNumber").value(contains(3, 4, 5, 6, 7, 9, 10)))
                .andExpect(jsonPath("$.rejections[*].reason").value(contains(
                        "OFF_GRID", "INVALID_DATE", "MISSING_FIELD", "UNKNOWN_SERVICE_CODE", "MALFORMED_ROW",
                        "CAPACITY_EXCEEDED", "DUPLICATE")))
                .andReturn().getResponse().getHeader("Location");

        // The report stays retrievable.
        mvc.perform(get(location)).andExpect(jsonPath("$.rejections", hasSize(7)));
        mvc.perform(get("/it-services/IT-MUELLER/appointment-imports"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].imported").value(2));
    }

    @Test
    void importedAppointmentsBlockSuggestionsAndBookings() throws Exception {
        upload("IT-MUELLER", MUELLER_CATALOG_FILE);

        // 2026-11-02 at Müller (capacity 2): DIA 08:00-08:30, DIA 08:30-09:00, LAP 09:00-11:00,
        // DIA 09:15-09:45, OSI 11:00-12:00. LAP + DIA saturate 09:15-09:45, so a 2h laptop service
        // starting before 09:45 would be the third appointment at some point.
        mvc.perform(get("/it-services/IT-MUELLER/appointment-suggestions")
                        .param("repairServiceId", "LAP")
                        .param("from", "2026-11-02T08:00:00Z")
                        .param("to", "2026-11-02T12:00:00Z"))
                .andExpect(jsonPath("$[*].start").value(contains("2026-11-02T09:45:00Z", "2026-11-02T10:00:00Z")));

        mvc.perform(get("/it-services/IT-MUELLER/appointment-suggestions")
                        .param("repairServiceId", "DIA")
                        .param("from", "2026-11-02T09:00:00Z")
                        .param("to", "2026-11-02T10:30:00Z"))
                .andExpect(jsonPath("$[*].start").value(contains("2026-11-02T09:45:00Z", "2026-11-02T10:00:00Z")));

        mvc.perform(post("/it-services/IT-MUELLER/appointments").contentType("application/json")
                        .content("""
                                {"customerNumber":"C-1","start":"2026-11-02T09:15:00Z","repairServiceId":"DIA"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void sameServiceCodeUsesTheDurationOfTheTargetProvider() throws Exception {
        byte[] csv = "KdnNr,APP_DATE,SERVICE\n1,2026-11-09T08:00Z,DIA\n".getBytes();

        upload("IT-MUELLER", "dia.csv", csv).andExpect(jsonPath("$.imported").value(1));
        upload("CS-SCHNEIDER", "dia.csv", csv).andExpect(jsonPath("$.imported").value(1));

        mvc.perform(get("/it-services/IT-MUELLER/appointments"))
                .andExpect(jsonPath("$[0].end").value("2026-11-09T08:30:00Z"));
        mvc.perform(get("/it-services/CS-SCHNEIDER/appointments"))
                .andExpect(jsonPath("$[0].end").value("2026-11-09T08:15:00Z"));
    }

    @Test
    void fileOrderDecidesWhichConflictingRowIsImported() throws Exception {
        // Capacity 2 at Müller: the first two rows win, the third is rejected – regardless of customer number.
        byte[] csv = """
                KdnNr,APP_DATE,SERVICE
                Z-LAST,2026-11-09T08:00Z,LAP
                A-FIRST,2026-11-09T09:00Z,OSI
                M-MIDDLE,2026-11-09T09:30Z,DIA
                """.getBytes();

        upload("IT-MUELLER", "order.csv", csv)
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.rejections[0].lineNumber").value(4))
                .andExpect(jsonPath("$.rejections[0].rawLine").value("M-MIDDLE,2026-11-09T09:30Z,DIA"))
                .andExpect(jsonPath("$.rejections[0].reason").value("CAPACITY_EXCEEDED"));
    }

    @Test
    void importRespectsAppointmentsBookedBefore() throws Exception {
        for (String customer : new String[] {"B-1", "B-2"}) {
            mvc.perform(post("/it-services/IT-MUELLER/appointments").contentType("application/json")
                            .content("""
                                    {"customerNumber":"%s","start":"2026-11-09T08:00:00Z","repairServiceId":"LAP"}
                                    """.formatted(customer)))
                    .andExpect(status().isCreated());
        }

        upload("IT-MUELLER", "late.csv", "KdnNr,APP_DATE,SERVICE\nL-1,2026-11-09T09:45Z,DIA\nL-2,2026-11-09T10:00Z,DIA\n".getBytes())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejections[0].reason").value("CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.rejections[0].rawLine").value("L-1,2026-11-09T09:45Z,DIA"));
    }

    @Test
    void rejectsImportedAppointmentsOutsideOpeningHours() throws Exception {
        byte[] csv = """
                KdnNr,APP_DATE,SERVICE
                N-1,2026-11-02T03:00Z,DIA
                N-2,2026-11-08T09:00Z,DIA
                N-3,2026-11-02T16:00Z,LAP
                N-4,2026-11-02T16:00Z,DIA
                """.getBytes();

        upload("IT-MUELLER", "night.csv", csv)
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejections[*].reason").value(contains(
                        "OUTSIDE_OPENING_HOURS", "OUTSIDE_OPENING_HOURS", "OUTSIDE_OPENING_HOURS")))
                .andExpect(jsonPath("$.rejections[*].lineNumber").value(contains(2, 3, 4)));
    }

    @Test
    void rejectsATooLongCustomerNumberAsARowNotTheWholeFile() throws Exception {
        String tooLong = "K".repeat(65);
        byte[] csv = ("KdnNr,APP_DATE,SERVICE\n" + tooLong + ",2026-11-02T08:00Z,DIA\nOK-1,2026-11-02T09:00Z,DIA\n").getBytes();

        upload("IT-MUELLER", "long.csv", csv)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejections[0].lineNumber").value(2))
                .andExpect(jsonPath("$.rejections[0].reason").value("INVALID_CUSTOMER_NUMBER"));
    }

    @Test
    void rejectsFilesThatCannotBeProcessedAtAll() throws Exception {
        upload("IT-MUELLER", "wrong.csv", "customer,date,code\n1,2026-11-02T08:00Z,DIA\n".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Expected header KdnNr,APP_DATE,SERVICE")));
        upload("IT-MUELLER", "empty.csv", new byte[0]).andExpect(status().isBadRequest());
        upload("IT-MUELLER", "appointments.xlsx", Files.readAllBytes(MUELLER_CATALOG_FILE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only .csv files can be imported"));
        upload("IT-MUELLER", "appointments.CSV", Files.readAllBytes(MUELLER_CATALOG_FILE)).andExpect(status().isCreated());
        upload("UNKNOWN", MUELLER_CATALOG_FILE).andExpect(status().isNotFound());
    }
}
