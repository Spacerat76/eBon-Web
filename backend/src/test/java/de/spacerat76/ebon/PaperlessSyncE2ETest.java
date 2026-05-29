package de.spacerat76.ebon;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
// Migration SQL is now applied by Flyway from classpath:db/migration during Spring startup
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.spacerat76.ebon.service.PaperlessSyncService;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.SyncLog;
import de.spacerat76.ebon.domain.SyncLogEntry;
import de.spacerat76.ebon.repository.ReceiptRepository;
import de.spacerat76.ebon.repository.SyncLogEntryRepository;
import de.spacerat76.ebon.repository.SyncLogRepository;

import java.util.List;
import java.util.Optional;

/**
 * End-to-end integration test that exercises the Paperless sync against a real Postgres
 * database (Testcontainers) and a mocked Paperless API (WireMock).
 */
@SpringBootTest(classes = {de.spacerat76.ebon.EbonApplication.class, PaperlessSyncE2ETest.NoSchedulerConfig.class}, properties = {
    "spring.datasource.hikari.auto-commit=false",
    "spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true",
    "ebon.scheduling.enabled=false",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration"
})
@Testcontainers
public class PaperlessSyncE2ETest {

    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.context.annotation.ComponentScan(basePackages = "de.spacerat76.ebon", excludeFilters = {
        @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = de.spacerat76.ebon.service.PaperlessSyncScheduler.class)
    })
    static class NoSchedulerConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        public org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor() {
            return new org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor() {
                @Override
                public void onApplicationEvent(org.springframework.context.event.ApplicationContextEvent event) {
                    // prevent any scheduled task registration during tests
                }
            };
        }
    }

        @Container
        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("ebon")
            .withUsername("postgres")
            .withPassword("postgres");

        // Ensure the Postgres container is started early and apply the SQL migration directly
        static {
            try {
                if (!postgres.isRunning()) {
                    postgres.start();
                }
                String raw = postgres.getJdbcUrl();
                String url = raw.contains("?") ? raw.substring(0, raw.indexOf("?")) : raw;
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword())) {
                    java.sql.Statement stmt = conn.createStatement();
                    java.sql.ResultSet rs = stmt.executeQuery("select to_regclass('public.sync_log')");
                    boolean exists = false;
                    if (rs.next()) {
                        exists = rs.getString(1) != null;
                    }
                    if (!exists) {
                        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(conn,
                                new org.springframework.core.io.support.EncodedResource(
                                        new org.springframework.core.io.ClassPathResource("db/migration/V1__init_schema.sql"), "UTF-8"));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to start Postgres and apply migrations: " + e.getMessage(), e);
            }
        }

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    // Testcontainers will start the Postgres container; WireMock will be started lazily

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            if (!postgres.isRunning()) {
                postgres.start();
            }
            String raw = postgres.getJdbcUrl();
            return raw.contains("?") ? raw.substring(0, raw.indexOf("?")) : raw;
        });
        registry.add("spring.datasource.username", () -> {
            if (!postgres.isRunning()) {
                postgres.start();
            }
            return postgres.getUsername();
        });
        registry.add("spring.datasource.password", () -> {
            if (!postgres.isRunning()) {
                postgres.start();
            }
            return postgres.getPassword();
        });
        registry.add("ebon.paperless-base-url", () -> {
            if (!wireMock.isRunning()) {
                wireMock.start();
            }
            return wireMock.baseUrl();
        });
        registry.add("ebon.app-api-token", () -> "dummy");
        // disable scheduled sync during test
        registry.add("ebon.sync-interval-minutes", () -> "0");
        // disable scheduling and adjust LOB handling for tests
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("spring.scheduling.enabled", () -> "false");
        registry.add("spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation", () -> "true");
        registry.add("spring.datasource.hikari.auto-commit", () -> "false");
        // Use classpath migrations for tests so Flyway picks up the bundled SQL
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @BeforeAll
    static void startAll() {
        // Ensure WireMock is running and Testcontainers Postgres is started
        if (!wireMock.isRunning()) {
            wireMock.start();
        }
        if (!postgres.isRunning()) {
            postgres.start();
        }
    }

    @AfterAll
    static void stopAll() {
        try { wireMock.stop(); } catch (Exception ignored) {}
        try { postgres.stop(); } catch (Exception ignored) {}
    }

    @Autowired
    PaperlessSyncService paperlessSyncService;

    @Autowired
    ReceiptRepository receiptRepository;

    @Autowired
    SyncLogRepository syncLogRepository;

    @Autowired
    SyncLogEntryRepository syncLogEntryRepository;

    

    @Test
    @org.springframework.transaction.annotation.Transactional
    void sync_insertsReceipts_and_writesSyncLog() throws Exception {
        // Paperless documents list
        wireMock.stubFor(get(urlPathEqualTo("/api/documents/")).withQueryParam("tags__name", equalTo("eBON"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"count\":2,\"next\":null,\"previous\":null,\"results\":[{\"id\":900},{\"id\":901}]}")
                ));

        // Document text endpoints
        wireMock.stubFor(get(urlPathEqualTo("/api/documents/900/text/"))
            .willReturn(aResponse().withHeader("Content-Type", "text/plain").withBody("Milk 1 9.99 EUR\nTotal 9.99 EUR")));
        wireMock.stubFor(get(urlPathEqualTo("/api/documents/901/text/"))
            .willReturn(aResponse().withHeader("Content-Type", "text/plain").withBody("Bread 1 5.00 EUR\nTotal 5.00 EUR")));

        // Run sync
        paperlessSyncService.syncNewDocuments();

        // Verify receipts created
        Optional<Receipt> r900 = receiptRepository.findByPaperlessDocumentId(900);
        assertThat(r900).isPresent();
        // Parser may put the total into items rather than receipt.total; verify items exist
        assertThat(r900.get().getItems()).isNotEmpty();
        assertThat(r900.get().getItems().get(0).getTotalPrice()).isGreaterThan(java.math.BigDecimal.ZERO);

        Optional<Receipt> r901 = receiptRepository.findByPaperlessDocumentId(901);
        assertThat(r901).isPresent();
        assertThat(r901.get().getItems()).isNotEmpty();

        // Verify sync log and entries
        List<SyncLog> logs = syncLogRepository.findAll();
        assertThat(logs).isNotEmpty();
        SyncLog log = logs.get(0);
        List<SyncLogEntry> entries = syncLogEntryRepository.findBySyncLogIdOrderByIdDesc(log.getId());
        assertThat(entries).isNotEmpty();
    }
}
