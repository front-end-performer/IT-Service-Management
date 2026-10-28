package de.challenge.appointments.acceptance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.challenge.appointments.IntegrationTest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base of the task acceptance suite (scenario ids as in docs/test-coverage.md). All tests go through
 * the REST API against PostgreSQL. Times are UTC on Monday 2026-11-02, the date of the legacy data;
 * "now" is fixed to 2026-10-28T12:00Z.
 */
@AutoConfigureMockMvc
abstract class AcceptanceTest extends IntegrationTest {

    static final String MUELLER = "IT-MUELLER";
    static final String SCHNEIDER = "CS-SCHNEIDER";

    @Autowired
    MockMvc mvc;

    /** {@code at("08:00")} is 2026-11-02T08:00:00Z. */
    static String at(String hhmm) {
        return "2026-11-02T" + hhmm + (hhmm.length() == 5 ? ":00Z" : "Z");
    }

    ResultActions book(String provider, String serviceCode, String customer, String start) throws Exception {
        return mvc.perform(post("/it-services/{p}/appointments", provider)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerNumber":"%s","start":"%s","repairServiceId":"%s"}
                        """.formatted(customer, start, serviceCode)));
    }

    /** Books and asserts 201; returns the appointment id. */
    String booked(String provider, String serviceCode, String customer, String start) throws Exception {
        String body = book(provider, serviceCode, customer, start).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    ResultActions suggest(String provider, String serviceCode, String from, String to) throws Exception {
        return mvc.perform(get("/it-services/{p}/appointment-suggestions", provider)
                .param("repairServiceId", serviceCode).param("from", from).param("to", to));
    }

    List<String> suggestedStarts(String provider, String serviceCode, String from, String to) throws Exception {
        String body = suggest(provider, serviceCode, from, to).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].start");
    }

    ResultActions importCsv(String provider, String csv) throws Exception {
        return mvc.perform(multipart("/it-services/{p}/appointment-imports", provider)
                .file(new MockMultipartFile("file", "legacy.csv", "text/csv", csv.getBytes())));
    }

    ResultActions appointments(String provider, String... params) throws Exception {
        var request = get("/it-services/{p}/appointments", provider);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }
}
