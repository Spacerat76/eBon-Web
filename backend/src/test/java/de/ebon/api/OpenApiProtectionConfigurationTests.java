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
        "app.openapi.public-access=false"
})
class OpenApiProtectionConfigurationTests extends PostgresIntegrationTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void openApiDocsCanBeProtectedByConfiguration() throws Exception {
        HttpResponse<String> response = sendGet("/v3/api-docs", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void swaggerUiCanBeProtectedByConfiguration() throws Exception {
        HttpResponse<String> response = sendGet("/swagger-ui.html", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void protectedOpenApiDocsAcceptBearerToken() throws Exception {
        HttpResponse<String> response = sendGet("/v3/api-docs", "test-token");

        assertThat(response.statusCode()).isEqualTo(200);
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
