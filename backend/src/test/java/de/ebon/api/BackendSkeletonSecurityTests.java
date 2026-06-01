package de.ebon.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.security.api-token=test-token")
class BackendSkeletonSecurityTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void healthIsPublic() throws Exception {
        HttpResponse<String> response = sendGet("/api/health", null);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Trace-Id")).isPresent();
        assertThat(body.get("status").asText()).isEqualTo("UP");
    }

    @Test
    void protectedEndpointRequiresBearerToken() throws Exception {
        HttpResponse<String> response = sendGet("/api/system/ping", null);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("path").asText()).isEqualTo("/api/system/ping");
        assertThat(body.get("traceId").asText()).isNotBlank();
    }

    @Test
    void protectedEndpointAcceptsValidBearerToken() throws Exception {
        HttpResponse<String> response = sendGet("/api/system/ping", "test-token");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("status").asText()).isEqualTo("OK");
    }

    @Test
    void openApiDocsAreProtected() throws Exception {
        HttpResponse<String> response = sendGet("/v3/api-docs", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void openApiDocsAreAvailableWithBearerToken() throws Exception {
        HttpResponse<String> response = sendGet("/v3/api-docs", "test-token");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("openapi").asText()).isNotBlank();
        assertThat(body.get("info").get("title").asText()).isEqualTo("eBon Expense Tracker API");
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
}
