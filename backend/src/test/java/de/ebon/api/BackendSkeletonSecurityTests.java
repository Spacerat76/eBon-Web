package de.ebon.api;

import de.ebon.support.PostgresIntegrationTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.security.api-token=test-token")
class BackendSkeletonSecurityTests extends PostgresIntegrationTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    // Verifies the public health endpoint remains available without authentication for health checks.
    @Test
    void healthIsPublic() throws Exception {
        HttpResponse<String> response = sendGet("/api/health", null);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Trace-Id")).isPresent();
        assertThat(body.get("status").asString()).isEqualTo("UP");
    }

    // Verifies protected API endpoints return the structured unauthorized error without a bearer token.
    @Test
    void protectedEndpointRequiresBearerToken() throws Exception {
        HttpResponse<String> response = sendGet("/api/system/ping", null);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asString()).isEqualTo("Unauthorized");
        assertThat(body.get("path").asString()).isEqualTo("/api/system/ping");
        assertThat(body.get("traceId").asString()).isNotBlank();
    }

    // Verifies the single-user bearer token grants access to protected endpoints.
    @Test
    void protectedEndpointAcceptsValidBearerToken() throws Exception {
        HttpResponse<String> response = sendGet("/api/system/ping", "test-token");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("status").asString()).isEqualTo("OK");
    }

    // Verifies the protected system info endpoint exposes the central application version for UI/build checks.
    @Test
    void systemInfoReturnsApplicationVersion() throws Exception {
        HttpResponse<String> response = sendGet("/api/system/info", "test-token");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("name").asString()).isEqualTo("eBon Expense Tracker");
        assertThat(body.get("version").asString()).isNotBlank();
    }

    // Verifies local development can load OpenAPI docs without authentication by default.
    @Test
    void openApiDocsArePublicByDefault() throws Exception {
        HttpResponse<String> response = sendGet("/v3/api-docs", null);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("openapi").asString()).isNotBlank();
        assertThat(body.get("info").get("title").asString()).isEqualTo("eBon Expense Tracker API");
        assertThat(body.get("info").get("version").asString()).isNotBlank();
    }

    // Verifies Swagger UI follows the configured local-development redirect and loads unauthenticated by default.
    @Test
    void swaggerUiIsPublicByDefault() throws Exception {
        HttpResponse<String> response = sendGet("/swagger-ui.html", null);
        HttpResponse<String> indexResponse = sendGet("/swagger-ui/index.html", null);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains("/swagger-ui/index.html");
        assertThat(indexResponse.statusCode()).isEqualTo(200);
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
