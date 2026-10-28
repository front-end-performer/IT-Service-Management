package de.challenge.appointments.api;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** A booking that cannot get the provider lock in time gets 503 + Retry-After, never 500 and never a booking. */
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
}
