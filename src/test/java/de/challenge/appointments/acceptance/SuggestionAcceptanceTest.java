package de.challenge.appointments.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S1–S9: suggestions consider duration, existing and imported appointments, capacity and the grid.
 * Decision (ADR 0004): {@code to} bounds the <em>end</em> of a suggestion, not only its start.
 */
class SuggestionAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("S1 Empty schedule, Müller DIA 08:00-10:00: every 15 minutes, end = start + 30, spec fields")
    void S1_emptySchedule() throws Exception {
        String body = suggest(MUELLER, "DIA", at("08:00"), at("10:00")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> suggestions = JsonPath.read(body, "$");

        assertThat(suggestions).extracting(s -> s.get("start")).containsExactly(
                at("08:00"), at("08:15"), at("08:30"), at("08:45"), at("09:00"), at("09:15"), at("09:30"));
        for (Map<String, Object> s : suggestions) {
            assertThat(s).containsOnlyKeys("start", "end", "serviceId", "repairServiceId");
            assertThat(Instant.parse((String) s.get("end")))
                    .isEqualTo(Instant.parse((String) s.get("start")).plus(Duration.ofMinutes(30)));
            assertThat(s).containsEntry("serviceId", MUELLER).containsEntry("repairServiceId", "DIA");
        }
    }

    @Test
    @DisplayName("S2 Two LAP 08:00-10:00 at Müller: no DIA suggestions in 08:00-10:00")
    void S2_fullCapacity() throws Exception {
        booked(MUELLER, "LAP", "S2-1", at("08:00"));
        booked(MUELLER, "LAP", "S2-2", at("08:00"));

        assertThat(suggestedStarts(MUELLER, "DIA", at("08:00"), at("10:00"))).isEmpty();
    }

    @Test
    @DisplayName("S3 One LAP 08:00-10:00 at Müller: DIA suggestions are still returned")
    void S3_oneSlotLeft() throws Exception {
        booked(MUELLER, "LAP", "S3", at("08:00"));

        assertThat(suggestedStarts(MUELLER, "DIA", at("08:00"), at("10:00"))).hasSize(7);
    }

    @Test
    @DisplayName("S4 Two DIA at 10:00, LAP requested: 08:00 suggested, 08:15-10:15 excluded, 10:30 onward suggested")
    void S4_longServiceAroundShortOnes() throws Exception {
        booked(MUELLER, "DIA", "S4-1", at("10:00"));
        booked(MUELLER, "DIA", "S4-2", at("10:00"));

        // 'to' bounds the end (ADR 0004), so the range runs to 13:00 for LAP starts up to 11:00 to appear.
        assertThat(suggestedStarts(MUELLER, "LAP", at("08:00"), at("13:00")))
                .containsExactly(at("08:00"), at("10:30"), at("10:45"), at("11:00"));
    }

    @Test
    @DisplayName("S5 Imported appointments fill a window: that window is not suggested")
    void S5_importedAppointmentsCount() throws Exception {
        importCsv(MUELLER, """
                KdnNr,APP_DATE,SERVICE
                S5-1,2026-11-02T08:00Z,LAP
                S5-2,2026-11-02T08:00Z,LAP
                """).andExpect(jsonPath("$.imported").value(2));

        assertThat(suggestedStarts(MUELLER, "DIA", at("08:00"), at("10:30"))).containsExactly(at("10:00"));
    }

    @Test
    @DisplayName("S6 Schneider DIA with two appointments at 08:00: 08:00 still suggested, end = start + 15")
    void S6_schneiderCapacityThree() throws Exception {
        booked(SCHNEIDER, "DIA", "S6-1", at("08:00"));
        booked(SCHNEIDER, "DIA", "S6-2", at("08:00"));

        suggest(SCHNEIDER, "DIA", at("08:00"), at("09:00"))
                .andExpect(jsonPath("$[0].start").value(at("08:00")))
                .andExpect(jsonPath("$[0].end").value(at("08:15")));
    }

    @Test
    @DisplayName("S7 Every returned suggestion can be booked")
    void S7_everySuggestionIsBookable() throws Exception {
        List<String> starts = suggestedStarts(MUELLER, "DIA", at("08:00"), at("10:00"));

        assertThat(starts).isNotEmpty();
        for (String start : starts) {
            booked(MUELLER, "DIA", "S7-" + start, start);
        }
    }

    @Test
    @DisplayName("S8 'from' after 'to' or a missing required parameter gives 400")
    void S8_invalidRequests() throws Exception {
        suggest(MUELLER, "DIA", at("10:00"), at("08:00")).andExpect(status().isBadRequest());
        String path = "/it-services/" + MUELLER + "/appointment-suggestions";
        mvc.perform(get(path).param("from", at("08:00")).param("to", at("10:00"))).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("repairServiceId", "DIA").param("to", at("10:00"))).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("repairServiceId", "DIA").param("from", at("08:00"))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("S9 Unknown provider or unknown service gives 404")
    void S9_unknownProviderOrService() throws Exception {
        suggest("UNKNOWN", "DIA", at("08:00"), at("10:00")).andExpect(status().isNotFound());
        suggest(MUELLER, "XYZ", at("08:00"), at("10:00")).andExpect(status().isNotFound());
        suggest(MUELLER, "VIR", at("08:00"), at("10:00")).andExpect(status().isNotFound());
    }
}
