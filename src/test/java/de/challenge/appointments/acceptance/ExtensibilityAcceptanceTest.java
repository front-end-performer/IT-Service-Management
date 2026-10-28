package de.challenge.appointments.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/**
 * X1: a third provider added purely through configuration – src/test/resources/application-x1.yml
 * is an ordinary config file, exactly what an operator would deploy. No code, no migration.
 */
@ActiveProfiles("x1")
class ExtensibilityAcceptanceTest extends AcceptanceTest {

    private static final String WEBER = "PC-HILFE-WEBER";

    @Test
    @DisplayName("X1 A third provider added through configuration only: listing, suggestions, booking, capacity")
    void X1_thirdProviderFromConfiguration() throws Exception {
        mvc.perform(get("/it-services")).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/it-services/{p}", WEBER)).andExpect(jsonPath("$.capacity").value(1));
        mvc.perform(get("/it-services/{p}/services", WEBER))
                .andExpect(jsonPath("$[?(@.code=='NET')].durationMinutes").value(45));

        assertThat(suggestedStarts(WEBER, "NET", at("08:00"), at("09:30"))).containsExactly(at("08:00"), at("08:15"), at("08:30"), at("08:45"));
        book(WEBER, "NET", "X1-1", at("08:00")).andExpect(status().isCreated()).andExpect(jsonPath("$.end").value(at("08:45")));
        book(WEBER, "NET", "X1-2", at("08:30")).andExpect(status().isConflict()); // capacity 1
        assertThat(suggestedStarts(WEBER, "NET", at("08:00"), at("09:30"))).containsExactly(at("08:45"));
    }
}
