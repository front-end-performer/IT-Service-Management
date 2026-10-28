package de.challenge.appointments.acceptance;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1–L8: staff view. Period semantics (ADR 0006): {@code from}/{@code to} are inclusive and select
 * every appointment running at some point of the period: {@code end > from AND start <= to}.
 */
class AppointmentManagementAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("L1 Müller's list contains only Müller's appointments, booked and imported")
    void L1_onlyOwnAppointmentsBothSources() throws Exception {
        booked(MUELLER, "DIA", "L1-booked", at("08:00"));
        importCsv(MUELLER, "KdnNr,APP_DATE,SERVICE\nL1-imported,2026-11-02T09:00Z,OSI\n");
        booked(SCHNEIDER, "DIA", "L1-other", at("08:00"));

        appointments(MUELLER)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].serviceId", everyItem(is(MUELLER))))
                .andExpect(jsonPath("$[*].source", containsInAnyOrder("BOOKED", "IMPORTED")));
    }

    @Test
    @DisplayName("L2 Period filter includes appointments at the boundaries (inclusive)")
    void L2_periodBoundaries() throws Exception {
        booked(MUELLER, "DIA", "ended-at-from", at("08:30"));  // 08:30-09:00: over when the period starts
        booked(MUELLER, "LAP", "running-at-from", at("08:00")); // 08:00-10:00: still running at 09:00
        booked(MUELLER, "DIA", "starts-at-from", at("09:00"));
        booked(MUELLER, "DIA", "starts-at-to", at("11:00"));
        booked(MUELLER, "DIA", "starts-after-to", at("11:15"));

        appointments(MUELLER, "from", at("09:00"), "to", at("11:00"))
                .andExpect(jsonPath("$[*].customerNumber", containsInAnyOrder("running-at-from", "starts-at-from", "starts-at-to")));
    }

    @Test
    @DisplayName("L3 Service filter returns only that service")
    void L3_serviceFilter() throws Exception {
        booked(MUELLER, "DIA", "L3-1", at("08:00"));
        booked(MUELLER, "OSI", "L3-2", at("08:00"));

        appointments(MUELLER, "repairServiceId", "OSI")
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].repairServiceId").value("OSI"));
    }

    @Test
    @DisplayName("L4 Time and service filters together: both conditions apply")
    void L4_combinedFilters() throws Exception {
        booked(MUELLER, "DIA", "L4-match", at("08:00"));
        booked(MUELLER, "OSI", "L4-other-service", at("08:00"));
        booked(MUELLER, "DIA", "L4-other-time", at("14:00"));

        appointments(MUELLER, "from", at("07:00"), "to", at("09:00"), "repairServiceId", "DIA")
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].customerNumber").value("L4-match"));
    }

    @Test
    @DisplayName("L5 Customer number filter returns only that customer")
    void L5_customerFilter() throws Exception {
        booked(MUELLER, "DIA", "L5-a", at("08:00"));
        booked(MUELLER, "DIA", "L5-b", at("09:00"));

        appointments(MUELLER, "customerNumber", "L5-b")
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].customerNumber").value("L5-b"));
    }

    @Test
    @DisplayName("L6 Details of an existing appointment: 200 with all fields required by the spec")
    void L6_details() throws Exception {
        String id = booked(MUELLER, "LAP", "L6", at("08:00"));

        mvc.perform(get("/it-services/{p}/appointments/{id}", MUELLER, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerNumber").value("L6"))
                .andExpect(jsonPath("$.start").value(at("08:00")))
                .andExpect(jsonPath("$.end").value(at("10:00")))
                .andExpect(jsonPath("$.serviceId").value(MUELLER))
                .andExpect(jsonPath("$.serviceName").value("IT-Service Müller"))
                .andExpect(jsonPath("$.repairServiceId").value("LAP"))
                .andExpect(jsonPath("$.repairServiceName").value("Laptop-Service"));
    }

    @Test
    @DisplayName("L7 Unknown appointment, or one belonging to another provider, gives 404")
    void L7_unknownOrForeignAppointment() throws Exception {
        String id = booked(MUELLER, "DIA", "L7", at("08:00"));

        mvc.perform(get("/it-services/{p}/appointments/{id}", MUELLER, "does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get("/it-services/{p}/appointments/{id}", SCHNEIDER, id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("L8 Appointments of an unknown provider give 404")
    void L8_unknownProvider() throws Exception {
        appointments("UNKNOWN").andExpect(status().isNotFound());
    }
}
