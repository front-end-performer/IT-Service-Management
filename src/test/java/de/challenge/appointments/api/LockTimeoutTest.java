package de.challenge.appointments.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.challenge.appointments.IntegrationTest;
import de.challenge.appointments.LockProbe;
import de.challenge.appointments.catalog.ProviderRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A booking or import that cannot get the provider lock in time gets 503 + Retry-After, never 500 and never a
 * booking. An import that times out is recorded as FAILED.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "appointments.lock-timeout-ms=500")
class LockTimeoutTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ProviderRepository providers;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void lockTimeoutIsAnsweredWith503() throws Exception {
        var released = LockProbe.holdProviderLock(transactions, providers, "IT-MUELLER", Duration.ofMillis(2000));

        mvc.perform(post("/it-services/IT-MUELLER/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerNumber":"C-1","start":"2026-11-02T09:00:00Z","repairServiceId":"DIA"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.title").value("Busy"));
        released.get();
    }

    @Test
    void importThatTimesOutIsAnsweredWith503AndRecordedAsFailed() throws Exception {
        var released = LockProbe.holdProviderLock(transactions, providers, "IT-MUELLER", Duration.ofMillis(2000));

        mvc.perform(multipart("/it-services/IT-MUELLER/appointment-imports").file(new MockMultipartFile("file", "busy.csv",
                        "text/csv", "KdnNr,APP_DATE,SERVICE\nC-1,2026-11-02T09:00Z,DIA\n".getBytes())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"));
        released.get();

        mvc.perform(get("/it-services/IT-MUELLER/appointment-imports"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].failureMessage").value(org.hamcrest.Matchers.containsString("lock timeout")));
    }
}
