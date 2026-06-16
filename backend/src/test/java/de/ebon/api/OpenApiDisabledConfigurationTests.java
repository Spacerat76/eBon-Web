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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.security.api-token=test-token",
        "app.openapi.public-access=false",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiDisabledConfigurationTests extends PostgresIntegrationTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    // Verifies hardened runtime configuration can remove the OpenAPI JSON endpoint instead of exposing it.
    @Test
    void disabledOpenApiDocsReturnNotFoundForAuthenticatedCaller() throws Exception {
        HttpResponse<String> response = sendGet("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    // Verifies hardened runtime configuration can remove Swagger UI without turning missing resources into 500s.
    @Test
    void disabledSwaggerUiReturnsNotFoundForAuthenticatedCaller() throws Exception {
        HttpResponse<String> response = sendGet("/swagger-ui.html");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer test-token")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
