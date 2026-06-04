package de.ebon.api;

import de.ebon.paperless.PaperlessClient;
import de.ebon.paperless.PaperlessDocument;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.security.api-token=test-token",
        "app.sync.scheduler.enabled=false"
})
class SyncApiTests extends PostgresIntegrationTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("truncate sync_log_entry, sync_log, receipt_item, receipt restart identity cascade");
    }

    // Verifies sync status is protected because it exposes integration state and timing.
    @Test
    void syncStatusRequiresAuthentication() throws Exception {
        HttpResponse<String> response = sendGet("/api/sync/status", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // Verifies trigger, status, and log endpoints work together through the authenticated HTTP API.
    @Test
    void triggerStatusAndLogAreAvailableWithBearerToken() throws Exception {
        HttpResponse<String> triggerResponse = sendPost("/api/sync/trigger", "test-token");
        waitUntilSyncFinished();

        HttpResponse<String> statusResponse = sendGet("/api/sync/status", "test-token");
        HttpResponse<String> logResponse = sendGet("/api/sync/log", "test-token");
        JsonNode triggerBody = objectMapper.readTree(triggerResponse.body());
        JsonNode statusBody = objectMapper.readTree(statusResponse.body());
        JsonNode logBody = objectMapper.readTree(logResponse.body());

        assertThat(triggerResponse.statusCode()).isEqualTo(202);
        assertThat(triggerBody.get("message").asString()).isEqualTo("Sync gestartet");
        assertThat(statusResponse.statusCode()).isEqualTo(200);
        assertThat(statusBody.get("lastSyncStatus").asString()).isEqualTo("SUCCESS");
        assertThat(statusBody.get("isSyncing").asBoolean()).isFalse();
        assertThat(logResponse.statusCode()).isEqualTo(200);
        assertThat(logBody.get("content").size()).isEqualTo(1);
    }

    private void waitUntilSyncFinished() throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            HttpResponse<String> response = sendGet("/api/sync/status", "test-token");
            JsonNode body = objectMapper.readTree(response.body());
            if (!body.get("isSyncing").asBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private HttpResponse<String> sendGet(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPost(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class FakePaperlessClientConfig {

        @Bean
        @Primary
        PaperlessClient paperlessClient() {
            return () -> List.of(new PaperlessDocument(
                    9001,
                    "API Test",
                    null,
                    """
                            REWE
                            01.01.2026
                            API Test Artikel 1,00
                            Summe 1,00
                            """));
        }
    }
}
