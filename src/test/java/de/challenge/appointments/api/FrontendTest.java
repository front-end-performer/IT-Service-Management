package de.challenge.appointments.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.challenge.appointments.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The Ext JS app and its vendored runtime are served from the executable JAR. */
@AutoConfigureMockMvc
class FrontendTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void servesTheExtJsApplication() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("vendor/extjs-6.2.0/ext-all.js")));
        for (String resource : new String[] {
                "/vendor/extjs-6.2.0/ext-all.js",
                "/vendor/extjs-6.2.0/classic/theme-triton/theme-triton.js",
                "/vendor/extjs-6.2.0/classic/theme-triton/resources/theme-triton-all.css",
                "/app/i18n.js", "/app/Api.js", "/app/app.js", "/app/app.css", "/app/view/ProviderCombo.js", "/app/view/BookingPanel.js",
                "/app/view/AppointmentsPanel.js", "/app/view/ImportPanel.js"}) {
            mvc.perform(get(resource)).andExpect(status().isOk());
        }
    }
}
