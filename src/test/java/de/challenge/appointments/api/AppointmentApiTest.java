package de.challenge.appointments.api;

import static org.assertj.core.api.Assertions.assertThat;
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
    void returnsASingleProvider() throws Exception {
        mvc.perform(get(MUELLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("IT-MUELLER"))
                .andExpect(jsonPath("$.name").value("IT-Service Müller"))
                .andExpect(jsonPath("$.capacity").value(2));
    }

    @Test
    void exposesTimeZoneAndOpeningHoursOfEachProvider() throws Exception {
        mvc.perform(get(MUELLER))
                .andExpect(jsonPath("$.timeZone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.openingHours.MONDAY").value("08:00-18:00"))
                .andExpect(jsonPath("$.openingHours.SATURDAY").value("09:00-13:00"))
                .andExpect(jsonPath("$.openingHours.SUNDAY").doesNotExist());
        mvc.perform(get("/it-services"))
                .andExpect(jsonPath("$[?(@.id=='CS-SCHNEIDER')].openingHours.FRIDAY").value("08:00-17:00"));
    }

    @Test
    void pagesThroughAppointmentsWithTotalCount() throws Exception {
        for (String start : new String[] {"08:00", "08:30", "09:00", "09:30", "10:00"}) {
            book(MUELLER, "DIA", "P-" + start, "2026-11-02T" + start + ":00Z").andExpect(status().isCreated());
        }

        mvc.perform(get(MUELLER + "/appointments").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(jsonPath("$[*].customerNumber").value(org.hamcrest.Matchers.contains("P-09:00", "P-09:30")));
        mvc.perform(get(MUELLER + "/appointments").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$[*].customerNumber").value(org.hamcrest.Matchers.contains("P-10:00")));
        // Paging is opt-in: without page/size the spec's behaviour applies and everything is returned.
        mvc.perform(get(MUELLER + "/appointments"))
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(jsonPath("$", hasSize(5)));
        mvc.perform(get(MUELLER + "/appointments").param("size", "2"))
                .andExpect(jsonPath("$[*].customerNumber").value(org.hamcrest.Matchers.contains("P-08:00", "P-08:30")));
        mvc.perform(get(MUELLER + "/appointments").param("page", "0"))
                .andExpect(jsonPath("$", hasSize(5)));
        mvc.perform(get(MUELLER + "/appointments").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(MUELLER + "/appointments").param("size", "501")).andExpect(status().isBadRequest());
        mvc.perform(get(MUELLER + "/appointments").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void errorsAreProblemDetails() throws Exception {
        book(MUELLER, "DIA", "C-1", "2026-11-02T09:10:00Z")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsBodiesWithMissingFields() throws Exception {
        mvc.perform(post(MUELLER + "/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerNumber":"C-1","start":"2026-11-02T09:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post(MUELLER + "/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerNumber":"C-1","repairServiceId":"DIA"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post(MUELLER + "/appointments").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
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
    void acceptsTimesWithoutSeconds() throws Exception {
        // The legacy CSV writes 2026-11-02T08:00Z; the API accepts the same short form.
        book(MUELLER, "DIA", "C-1", "2026-11-02T08:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.start").value("2026-11-02T08:00:00Z"));
        mvc.perform(get(MUELLER + "/appointments").param("from", "2026-11-02T08:00Z").param("to", "2026-11-02T08:00Z"))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA")
                        .param("from", "2026-11-02T08:00Z").param("to", "2026-11-02T10:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].start").value(org.hamcrest.Matchers.contains(
                        "2026-11-02T08:00:00Z", "2026-11-02T08:15:00Z", "2026-11-02T08:30:00Z")));
    }

    @Test
    void rejectsBookingsInThePast() throws Exception {
        // "Now" is 2026-10-28T12:00Z (FixedClockConfiguration).
        book(MUELLER, "DIA", "C-1", "2026-10-28T11:45:00Z")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Start 2026-10-28T11:45:00Z is in the past"));
        book(MUELLER, "DIA", "C-1", "2026-10-28T12:00:00Z").andExpect(status().isCreated());
    }

    @Test
    void neverSuggestsPastSlots() throws Exception {
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA")
                        .param("from", "2026-10-28T11:00:00Z").param("to", "2026-10-28T13:00:00Z"))
                .andExpect(jsonPath("$[*].start").value(org.hamcrest.Matchers.contains(
                        "2026-10-28T12:00:00Z", "2026-10-28T12:15:00Z", "2026-10-28T12:30:00Z")));
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA")
                        .param("from", "2026-10-27T08:00:00Z").param("to", "2026-10-27T18:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
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
    void peakConcurrencyNotOverlapCountDecides() throws Exception {
        // Müller, capacity 2: A 08:00-08:30, B 08:30-09:00, C 09:00-09:30 all overlap 08:00-10:00,
        // but only one of them runs at any instant, so a 2h laptop service still fits.
        book(MUELLER, "DIA", "A", "2026-11-02T08:00:00Z").andExpect(status().isCreated());
        book(MUELLER, "DIA", "B", "2026-11-02T08:30:00Z").andExpect(status().isCreated());
        book(MUELLER, "DIA", "C", "2026-11-02T09:00:00Z").andExpect(status().isCreated());

        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "LAP")
                        .param("from", "2026-11-02T08:00:00Z").param("to", "2026-11-02T10:00:00Z"))
                .andExpect(jsonPath("$[*].start").value(org.hamcrest.Matchers.contains("2026-11-02T08:00:00Z")));
        book(MUELLER, "LAP", "D", "2026-11-02T08:00:00Z").andExpect(status().isCreated());

        // Now two run in parallel during the whole morning: nothing else fits anywhere in 08:00-10:00.
        book(MUELLER, "DIA", "E", "2026-11-02T09:30:00Z").andExpect(status().isCreated()); // after C ends
        book(MUELLER, "DIA", "F", "2026-11-02T08:15:00Z").andExpect(status().isConflict());
        book(MUELLER, "DIA", "F", "2026-11-02T09:15:00Z").andExpect(status().isConflict()); // overlaps C and E
    }

    @Test
    void schneiderAllowsThreeParallelAppointmentsButNotFour() throws Exception {
        book(SCHNEIDER, "DAT", "C-1", "2026-11-02T08:00:00Z").andExpect(status().isCreated());
        book(SCHNEIDER, "VIR", "C-2", "2026-11-02T09:00:00Z").andExpect(status().isCreated());
        book(SCHNEIDER, "DIA", "C-3", "2026-11-02T09:30:00Z").andExpect(status().isCreated());

        book(SCHNEIDER, "DIA", "C-4", "2026-11-02T09:30:00Z").andExpect(status().isConflict());
        book(SCHNEIDER, "DIA", "C-4", "2026-11-02T09:45:00Z").andExpect(status().isCreated()); // DIA 09:30 ended
    }

    @Test
    void parallelHttpRequestsGet201UntilCapacityThen409() throws Exception {
        int requests = 12;
        var start = new java.util.concurrent.CountDownLatch(1);
        var statuses = java.util.Collections.synchronizedList(new java.util.ArrayList<Integer>());
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(requests)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < requests; i++) {
                String customer = "HTTP-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    statuses.add(book(MUELLER, "LAP", customer, "2026-11-02T09:00:00Z").andReturn().getResponse().getStatus());
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(2); // capacity of Müller
        mvc.perform(get(MUELLER + "/appointments")).andExpect(jsonPath("$", hasSize(2)));
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
    void suggestionsRoundAnUnalignedFromUpAndEndByTo() throws Exception {
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "OSI")
                        .param("from", "2026-11-02T08:07:00Z").param("to", "2026-11-02T10:00:00Z"))
                .andExpect(jsonPath("$[*].start").value(org.hamcrest.Matchers.contains(
                        "2026-11-02T08:15:00Z", "2026-11-02T08:30:00Z", "2026-11-02T08:45:00Z", "2026-11-02T09:00:00Z")))
                .andExpect(jsonPath("$[3].end").value("2026-11-02T10:00:00Z"));
    }

    @Test
    void suggestionsFollowEachProvidersOpeningHours() throws Exception {
        // Monday 2026-11-02, CET: Müller 08:00-18:00 local = 07:00-17:00Z, Schneider 08:00-17:00 = 07:00-16:00Z.
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "LAP")
                        .param("from", "2026-11-02T00:00:00Z").param("to", "2026-11-03T00:00:00Z"))
                .andExpect(jsonPath("$[0].start").value("2026-11-02T07:00:00Z"))
                .andExpect(jsonPath("$[-1:].end").value(org.hamcrest.Matchers.contains("2026-11-02T17:00:00Z")));
        mvc.perform(get(SCHNEIDER + "/appointment-suggestions").param("repairServiceId", "DAT")
                        .param("from", "2026-11-02T00:00:00Z").param("to", "2026-11-03T00:00:00Z"))
                .andExpect(jsonPath("$[0].start").value("2026-11-02T07:00:00Z"))
                .andExpect(jsonPath("$[-1:].end").value(org.hamcrest.Matchers.contains("2026-11-02T16:00:00Z")));
        // Sunday: closed.
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA")
                        .param("from", "2026-11-08T00:00:00Z").param("to", "2026-11-09T00:00:00Z"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void suggestionsFollowTheDaylightSavingSwitch() throws Exception {
        // Friday 2027-03-26 (CET) opens at 07:00Z, Monday 2027-03-29 (CEST) at 06:00Z – both 08:00 in Berlin.
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA")
                        .param("from", "2027-03-26T00:00:00Z").param("to", "2027-03-27T00:00:00Z"))
                .andExpect(jsonPath("$[0].start").value("2027-03-26T07:00:00Z"));
        mvc.perform(get(MUELLER + "/appointment-suggestions").param("repairServiceId", "DIA")
                        .param("from", "2027-03-29T00:00:00Z").param("to", "2027-03-30T00:00:00Z"))
                .andExpect(jsonPath("$[0].start").value("2027-03-29T06:00:00Z"))
                .andExpect(jsonPath("$[-1:].end").value(org.hamcrest.Matchers.contains("2027-03-29T16:00:00Z")));
    }

    @Test
    void rejectsBookingsOutsideOpeningHoursOrPastClosing() throws Exception {
        book(MUELLER, "DIA", "C-1", "2026-11-02T06:45:00Z") // 07:45 local
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("outside the opening hours")));
        book(MUELLER, "LAP", "C-1", "2026-11-02T16:00:00Z") // 17:00-19:00 local, past closing
                .andExpect(status().isBadRequest());
        book(MUELLER, "DIA", "C-1", "2026-11-08T09:00:00Z") // Sunday
                .andExpect(status().isBadRequest());
        book(MUELLER, "LAP", "C-1", "2026-11-02T15:00:00Z") // 16:00-18:00 local, ends at closing
                .andExpect(status().isCreated());
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
                .andExpect(jsonPath("$.repairServiceName").value("Betriebssysteminstallation"));
        mvc.perform(get(SCHNEIDER + "/appointments/" + id)).andExpect(status().isNotFound());
    }
}
