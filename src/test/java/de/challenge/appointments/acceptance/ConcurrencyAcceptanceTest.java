package de.challenge.appointments.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import de.challenge.appointments.Simultaneously;
import de.challenge.appointments.scheduling.CapacityCalculator;
import de.challenge.appointments.scheduling.TimeSlot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** K1–K2: simultaneous HTTP requests against PostgreSQL, the database the application ships with. */
class ConcurrencyAcceptanceTest extends AcceptanceTest {

    private record Request(String serviceCode, String customer, String start) {
    }

    private List<Integer> fireSimultaneously(List<Request> requests) throws Exception {
        return Simultaneously.run(requests.size(), i -> {
            Request r = requests.get(i);
            return () -> book(MUELLER, r.serviceCode(), r.customer(), r.start()).andReturn().getResponse().getStatus();
        });
    }

    private int peakConcurrencyInDatabase(String providerId) {
        List<TimeSlot> stored = jdbc.query("select start_at, end_at from appointment where provider_id = ?",
                (rs, n) -> new TimeSlot(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()), providerId);
        return CapacityCalculator.peakConcurrency(stored,
                new TimeSlot(Instant.parse("2026-11-02T00:00:00Z"), Instant.parse("2026-11-03T00:00:00Z")));
    }

    @Test
    @DisplayName("K1 20 parallel requests for the same Müller slot: exactly 2 get 201, 18 get 409")
    void K1_sameSlot() throws Exception {
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            requests.add(new Request("LAP", "K1-" + i, at("08:00")));
        }

        List<Integer> statuses = fireSimultaneously(requests);

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(2);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(18);
        assertThat(peakConcurrencyInDatabase(MUELLER)).isEqualTo(2);
    }

    @Test
    @DisplayName("K2 Parallel requests for different overlapping slots: capacity never exceeded in the database")
    void K2_overlappingSlots() throws Exception {
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            requests.add(new Request("LAP", "K2-LAP-" + i, at("08:00")));
            requests.add(new Request("DIA", "K2-DIA-" + i, at("09:30")));
            requests.add(new Request("OSI", "K2-OSI-" + i, at("09:00")));
        }

        List<Integer> statuses = fireSimultaneously(requests);

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).contains(201);
        assertThat(peakConcurrencyInDatabase(MUELLER)).isBetween(1, 2);
    }
}
