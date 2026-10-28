package de.challenge.appointments.acceptance;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P1–P3: providers and services exactly as in the task's two tables. */
class ProviderAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("P1 Providers: Müller (capacity 2) and Schneider (capacity 3), each with id and name")
    void P1_listProviders() throws Exception {
        mvc.perform(get("/it-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id=='IT-MUELLER')].name").value("IT-Service Müller"))
                .andExpect(jsonPath("$[?(@.id=='IT-MUELLER')].capacity").value(2))
                .andExpect(jsonPath("$[?(@.id=='CS-SCHNEIDER')].name").value("Computer-Service Schneider"))
                .andExpect(jsonPath("$[?(@.id=='CS-SCHNEIDER')].capacity").value(3));
    }

    @Test
    @DisplayName("P2 Provider details give 200; an unknown provider gives 404")
    void P2_providerDetails() throws Exception {
        mvc.perform(get("/it-services/{p}", SCHNEIDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SCHNEIDER))
                .andExpect(jsonPath("$.name").value("Computer-Service Schneider"))
                .andExpect(jsonPath("$.capacity").value(3));
        mvc.perform(get("/it-services/{p}", "UNKNOWN")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("P3 Services: Müller DIA 30, LAP 120, OSI 60; Schneider DIA 15, VIR 60, DAT 180; with id, code and name")
    void P3_servicesPerProvider() throws Exception {
        mvc.perform(get("/it-services/{p}/services", MUELLER))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("DIA", "LAP", "OSI")))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder("DIA", "LAP", "OSI")))
                .andExpect(jsonPath("$[?(@.code=='DIA')].durationMinutes").value(30))
                .andExpect(jsonPath("$[?(@.code=='LAP')].durationMinutes").value(120))
                .andExpect(jsonPath("$[?(@.code=='OSI')].durationMinutes").value(60))
                .andExpect(jsonPath("$[?(@.code=='DIA')].name").value("PC-Diagnose"))
                .andExpect(jsonPath("$[?(@.code=='LAP')].name").value("Laptop-Service"))
                .andExpect(jsonPath("$[?(@.code=='OSI')].name").value("Betriebssysteminstallation"));
        mvc.perform(get("/it-services/{p}/services", SCHNEIDER))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("DIA", "VIR", "DAT")))
                .andExpect(jsonPath("$[?(@.code=='DIA')].durationMinutes").value(15))
                .andExpect(jsonPath("$[?(@.code=='VIR')].durationMinutes").value(60))
                .andExpect(jsonPath("$[?(@.code=='DAT')].durationMinutes").value(180))
                .andExpect(jsonPath("$[?(@.code=='VIR')].name").value("Virenentfernung"))
                .andExpect(jsonPath("$[?(@.code=='DAT')].name").value("Datenrettung"));
    }
}
