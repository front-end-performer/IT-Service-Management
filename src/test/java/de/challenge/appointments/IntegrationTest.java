package de.challenge.appointments;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base for tests against a real PostgreSQL (Testcontainers). The catalog from application.yml
 * is loaded once; appointments and imports are wiped before every test. "Now" is fixed, see
 * {@link FixedClockConfiguration}.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
public abstract class IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanSchedule() {
        jdbc.update("delete from appointment");
        jdbc.update("delete from import_rejection");
        jdbc.update("delete from import_batch");
    }
}
