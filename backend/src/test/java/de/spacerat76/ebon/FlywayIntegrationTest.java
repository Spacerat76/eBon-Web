package de.spacerat76.ebon;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Integration test; enable when Docker/Testcontainers available")
@SpringBootTest
class FlywayIntegrationTest {

        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("ebon")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("ebon.app-api-token", () -> "dummy");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsApplied() {
        String reg = jdbcTemplate.queryForObject("select to_regclass('public.receipt')", String.class);
        assertNotNull(reg, "receipt table should exist after Flyway migrations");
    }
}
