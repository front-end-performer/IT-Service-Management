package de.challenge.appointments.acceptance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** G1–G2: 15-minute grid. E1–E2: end time from the provider-specific duration. */
class GridAndEndTimeAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("G1 Bookings at 08:00, 08:15, 08:30 and 08:45 are accepted")
    void G1_quarterHoursAccepted() throws Exception {
        // DIA takes 30 min at Müller, so at most two of these run at once (capacity 2).
        for (String start : new String[] {"08:00", "08:15", "08:30", "08:45"}) {
            book(MUELLER, "DIA", "G1-" + start, at(start)).andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("G2 Bookings at 08:07 or 08:15:30 are rejected with 400")
    void G2_offGridRejected() throws Exception {
        book(MUELLER, "DIA", "G2", at("08:07")).andExpect(status().isBadRequest());
        book(MUELLER, "DIA", "G2", at("08:15:30")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("E1 Müller: DIA, LAP, OSI at 08:00 end at 08:30, 10:00, 09:00")
    void E1_muellerDurations() throws Exception {
        // Three different days: three appointments at 08:00 on one day would exceed capacity 2.
        book(MUELLER, "DIA", "E1", "2026-11-02T08:00:00Z").andExpect(jsonPath("$.end").value("2026-11-02T08:30:00Z"));
        book(MUELLER, "LAP", "E1", "2026-11-03T08:00:00Z").andExpect(jsonPath("$.end").value("2026-11-03T10:00:00Z"));
        book(MUELLER, "OSI", "E1", "2026-11-04T08:00:00Z").andExpect(jsonPath("$.end").value("2026-11-04T09:00:00Z"));
    }

    @Test
    @DisplayName("E2 Schneider: DIA at 08:00 ends at 08:15 (same code as Müller, different duration)")
    void E2_schneiderDia() throws Exception {
        book(SCHNEIDER, "DIA", "E2", at("08:00")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.end").value(at("08:15")));
    }
}
