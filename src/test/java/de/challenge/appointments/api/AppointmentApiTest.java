package de.challenge.appointments.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.challenge.appointments.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class AppointmentApiTest extends IntegrationTest {

    private static final String MUELLER = "/it-services/IT-MUELLER";
    private static final String SCHNEIDER = "/it-services/CS-SCHNEIDER";

    @Autowired
    private MockMvc mvc;

    private ResultActions book(String provider, String service, String customer, String start) throws Exception {
        return mvc.perform(post(provider + "/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerNumber":"%s","start":"%s","repairServiceId":"%s"}
                        """.formatted(customer, start, service)));
    }

    @Test
    void listsProvidersAndTheirServices() throws Exception {
        mvc.perform(get("/it-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id=='IT-MUELLER')].capacity").value(2))
                .andExpect(jsonPath("$[?(@.id=='CS-SCHNEIDER')].capacity").value(3));

        // Same code, different duration per provider.
        mvc.perform(get(MUELLER + "/services"))
                .andExpect(jsonPath("$[?(@.code=='DIA')].durationMinutes").value(30));
        mvc.perform(get(SCHNEIDER + "/services"))
                .andExpect(jsonPath("$[?(@.code=='DIA')].durationMinutes").value(15));

        mvc.perform(get("/it-services/UNKNOWN")).andExpect(status().isNotFound());
        mvc.perform(get("/it-services/UNKNOWN/services")).andExpect(status().isNotFound());
    }

    @Test
    void bookingComputesEndFromProviderSpecificDuration() throws Exception {
        book(MUELLER, "LAP", "C-1", "2026-11-02T09:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/it-services/IT-MUELLER/appointments/")))
                .andExpect(jsonPath("$.end").value("2026-11-02T11:00:00Z"))
                .andExpect(jsonPath("$.serviceId").value("IT-MUELLER"))
                .andExpect(jsonPath("$.serviceName").value("IT-Service Müller"))
                .andExpect(jsonPath("$.repairServiceId").value("LAP"))
                .andExpect(jsonPath("$.source").value("BOOKED"));

        book(SCHNEIDER, "DIA", "C-1", "2026-11-02T09:00:00Z")
                .andExpect(jsonPath("$.end").value("2026-11-02T09:15:00Z"));
    }

    @Test
    void acceptsStartWithOffsetAndStoresUtc() throws Exception {
        book(MUELLER, "DIA", "C-1", "2026-11-02T10:15:00+01:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.start").value("2026-11-02T09:15:00Z"));
    }

    @Test
    void rejectsStartOffTheQuarterHourGrid() throws Exception {
        book(MUELLER, "DIA", "C-1", "2026-11-02T09:10:00Z")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(startsWith("Start 2026-11-02T09:10:00Z is not on the 15-minute grid")));
    }

    @Test
    void rejectsInvalidBodies() throws Exception {
        book(MUELLER, "DIA", "", "2026-11-02T09:00:00Z").andExpect(status().isBadRequest());
        book(MUELLER, "DIA", "C-1", "not-a-date").andExpect(status().isBadRequest());
    }

    @Test
    void unknownProviderOrServiceIsNotFound() throws Exception {
        book("/it-services/UNKNOWN", "DIA", "C-1", "2026-11-02T09:00:00Z").andExpect(status().isNotFound());
        // VIR exists, but only at Schneider.
        book(MUELLER, "VIR", "C-1", "2026-11-02T09:00:00Z").andExpect(status().isNotFound());
    }

    @Test
    void bookingBeyondCapacityIsAConflict() throws Exception {
        book(MUELLER, "LAP", "C-1", "2026-11-02T09:00:00Z").andExpect(status().isCreated());
        book(MUELLER, "LAP", "C-2", "2026-11-02T09:00:00Z").andExpect(status().isCreated());

        // A short appointment starting mid-way through both laptop services would be the third in parallel.
        book(MUELLER, "DIA", "C-3", "2026-11-02T10:30:00Z")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Appointment not available"));
        // Touching the end of both is fine.
        book(MUELLER, "DIA", "C-3", "2026-11-02T11:00:00Z").andExpect(status().isCreated());
        // The other provider is unaffected.
        book(SCHNEIDER, "DAT", "C-3", "2026-11-02T09:00:00Z").andExpect(status().isCreated());
    }

    @Test
    void sameCustomerServiceAndStartIsADuplicate() throws Exception {
        book(SCHNEIDER, "VIR", "C-1", "2026-11-02T09:00:00Z").andExpect(status().isCreated());
        book(SCHNEIDER, "VIR", "C-1", "2026-11-02T09:00:00Z").andExpect(status().isConflict());
    }

    @Test
    void suggestionsRespectCapacityOverTheWholeDuration() throws Exception {
        book(MUELLER, "DIA", "C-1", "2026-11-02T09:30:00Z").andExpect(status().isCreated());
        book(MUELLER, "OSI", "C-2", "2026-11-02T09:15:00Z").andExpect(status().isCreated());

        // 2h laptop service between 08:00 and 12:00 must not cover 09:30-10:00, where 2 appointments run.
        mvc.perform(get(MUELLER + "/appointment-suggestions")
                        .param("repairServiceId", "LAP")
                        .param("from", "2026-11-02T08:00:00Z")
                        .param("to", "2026-11-02T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].start").value("2026-11-02T10:00:00Z"))
                .andExpect(jsonPath("$[0].end").value("2026-11-02T12:00:00Z"))
                .andExpect(jsonPath("$[0].serviceId").value("IT-MUELLER"))
                .andExpect(jsonPath("$[0].repairServiceId").value("LAP"));
    }

    @Test
    void suggestionsValidateTheSearchRange() throws Exception {
        var request = get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA");
        mvc.perform(request.param("from", "2026-11-02T12:00:00Z").param("to", "2026-11-02T08:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA")
                        .param("from", "2026-11-01T00:00:00Z").param("to", "2027-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA")
                        .param("from", "yesterday").param("to", "2026-11-02T08:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "XXX")
                        .param("from", "2026-11-02T08:00:00Z").param("to", "2026-11-02T09:00:00Z"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsAndFiltersAppointmentsOfOneProvider() throws Exception {
        book(MUELLER, "DIA", "C-1", "2026-11-02T08:00:00Z");
        book(MUELLER, "LAP", "C-2", "2026-11-02T09:00:00Z");
        book(MUELLER, "DIA", "C-3", "2026-11-03T08:00:00Z");
        book(SCHNEIDER, "DIA", "C-4", "2026-11-02T08:00:00Z");

        mvc.perform(get(MUELLER + "/appointments"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].customerNumber").value("C-1"));
        mvc.perform(get(MUELLER + "/appointments")
                        .param("from", "2026-11-02T00:00:00Z").param("to", "2026-11-02T23:59:59Z"))
                .andExpect(jsonPath("$", hasSize(2)));
        // An appointment still running at 'from' is part of the period.
        mvc.perform(get(MUELLER + "/appointments").param("from", "2026-11-02T10:00:00Z")
                        .param("to", "2026-11-02T10:00:00Z"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].repairServiceId").value("LAP"));
        mvc.perform(get(MUELLER + "/appointments").param("repairServiceId", "DIA"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get(MUELLER + "/appointments").param("customerNumber", "C-3"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void returnsDetailsOnlyForTheOwningProvider() throws Exception {
        String location = book(MUELLER, "OSI", "C-1", "2026-11-02T08:00:00Z")
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.repairServiceName").value("Betriebssystem-Installation"));
        mvc.perform(get(SCHNEIDER + "/appointments/" + id)).andExpect(status().isNotFound());
    }
}
