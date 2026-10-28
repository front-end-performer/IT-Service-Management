package de.challenge.appointments.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** B1–B6: booking with re-check. C10: each provider has its own capacity. */
class BookingAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("B1 Booking a suggested slot gives 201, a spec-conform body, and the appointment is persisted")
    void B1_bookSuggestion() throws Exception {
        String start = suggestedStarts(MUELLER, "OSI", at("08:00"), at("10:00")).getFirst();

        String location = book(MUELLER, "OSI", "B1", start)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.customerNumber").value("B1"))
                .andExpect(jsonPath("$.start").value(at("08:00")))
                .andExpect(jsonPath("$.end").value(at("09:00")))
                .andExpect(jsonPath("$.serviceId").value(MUELLER))
                .andExpect(jsonPath("$.serviceName").value("IT-Service Müller"))
                .andExpect(jsonPath("$.repairServiceId").value("OSI"))
                .andExpect(jsonPath("$.repairServiceName").value("Betriebssystem-Installation"))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.customerNumber").value("B1"));
        assertThat(jdbc.queryForObject("select count(*) from appointment", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("B2 A slot at full capacity gives 409")
    void B2_fullCapacity() throws Exception {
        booked(MUELLER, "LAP", "B2-1", at("08:00"));
        booked(MUELLER, "LAP", "B2-2", at("08:00"));

        book(MUELLER, "LAP", "B2-3", at("08:00")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("B3 A slot filled between suggestion and booking gives 409")
    void B3_filledAfterSuggestion() throws Exception {
        assertThat(suggestedStarts(MUELLER, "DIA", at("08:00"), at("08:30"))).containsExactly(at("08:00"));
        booked(MUELLER, "LAP", "other-1", at("08:00"));
        booked(MUELLER, "LAP", "other-2", at("08:00"));

        book(MUELLER, "DIA", "B3", at("08:00")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("B4 A slot filled by imported appointments gives 409")
    void B4_filledByImport() throws Exception {
        importCsv(MUELLER, """
                KdnNr,APP_DATE,SERVICE
                B4-1,2026-11-02T08:00Z,LAP
                B4-2,2026-11-02T08:00Z,LAP
                """).andExpect(jsonPath("$.imported").value(2));

        book(MUELLER, "DIA", "B4", at("09:00")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("B5 Unknown provider, unknown service, or a service the provider doesn't offer gives 404")
    void B5_notFound() throws Exception {
        book("UNKNOWN", "DIA", "B5", at("08:00")).andExpect(status().isNotFound());
        book(MUELLER, "XYZ", "B5", at("08:00")).andExpect(status().isNotFound());
        book(MUELLER, "VIR", "B5", at("08:00")).andExpect(status().isNotFound()); // VIR exists only at Schneider
    }

    @Test
    @DisplayName("B6 Missing customerNumber, start or repairServiceId gives 400")
    void B6_missingFields() throws Exception {
        for (String body : List.of(
                "{\"start\":\"%s\",\"repairServiceId\":\"DIA\"}".formatted(at("08:00")),
                "{\"customerNumber\":\"B6\",\"repairServiceId\":\"DIA\"}",
                "{\"customerNumber\":\"B6\",\"start\":\"%s\"}".formatted(at("08:00")))) {
            mvc.perform(post("/it-services/{p}/appointments", MUELLER).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("C10 Two DIA at Schneider 08:00, then two DIA at Müller 08:00 are allowed (own capacity per provider)")
    void C10_capacityPerProvider() throws Exception {
        booked(SCHNEIDER, "DIA", "C10-1", at("08:00"));
        booked(SCHNEIDER, "DIA", "C10-2", at("08:00"));
        booked(MUELLER, "DIA", "C10-3", at("08:00"));
        booked(MUELLER, "DIA", "C10-4", at("08:00"));
    }
}
