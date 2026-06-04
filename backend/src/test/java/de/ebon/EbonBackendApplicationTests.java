package de.ebon;

import de.ebon.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EbonBackendApplicationTests extends PostgresIntegrationTestSupport {

    // Verifies the Spring application context starts with Flyway, PostgreSQL, and test configuration wired together.
    @Test
    void contextLoads() {
    }
}
