package de.ebon.api;

import de.ebon.support.PostgresIntegrationTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.security.api-token=test-token",
        "app.sync.scheduler.enabled=false"
})
class ProductsApiContractTests extends PostgresIntegrationTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Verifies the protected product API supports the Phase-15a family, variant, rule, preview, and run contracts.
    @Test
    void productManagementEndpointsCreateDataAndExposeRulePreview() throws Exception {
        String marker = "PHASE15API" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        HttpResponse<String> unauthorized = send(HttpRequest.newBuilder()
                .uri(uri("/api/products/families"))
                .GET(), false);
        assertThat(unauthorized.statusCode()).isEqualTo(401);

        JsonNode family = body(post("/api/products/families", """
                {"name":"%s Mineralwasser","defaultCategoryId":null,"isActive":true}
                """.formatted(marker)));
        assertThat(family.get("name").asString()).isEqualTo(marker + " Mineralwasser");
        long familyId = family.get("id").asLong();

        JsonNode variant = body(post("/api/products/variants", """
                {"productFamilyId":%d,"name":"%s Mineralwasser 1 l","unitQuantity":1.000,
                 "unit":"l","packageQuantity":1,"totalQuantity":1.000,"totalUnit":"l","isActive":true}
                """.formatted(familyId, marker)));
        assertThat(variant.get("productFamilyId").asLong()).isEqualTo(familyId);
        long variantId = variant.get("id").asLong();

        JsonNode rule = body(post("/api/products/rules", """
                {"productFamilyId":%d,"productVariantId":%d,"storeName":"REWE",
                 "matchType":"CONTAINS","matchValue":"%s","priority":10,"isActive":true}
                """.formatted(familyId, variantId, marker)));
        assertThat(rule.get("productVariantId").asLong()).isEqualTo(variantId);

        Long receiptId = jdbcTemplate.queryForObject("""
                insert into receipt (paperless_document_id, receipt_date, store_name, total_amount, raw_text, parse_status)
                values (?, date '2026-06-22', 'REWE', 1.00, 'contract test receipt', 'PARSED')
                returning id
                """, Long.class, Math.abs(marker.hashCode()));
        jdbcTemplate.update("""
                insert into receipt_item (receipt_id, position_index, description, total_price)
                values (?, 0, ?, 1.00)
                """, receiptId, marker);

        JsonNode preview = body(post("/api/products/rules/preview", """
                {"storeName":"REWE","matchType":"CONTAINS","matchValue":"%s"}
                """.formatted(marker)));
        assertThat(preview.get("matchingItemsCount").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode run = body(post("/api/products/assignments/run", """
                {"receiptId":null,"openOnly":true}
                """));
        assertThat(run.get("changedItemsCount").asLong()).isGreaterThanOrEqualTo(1);
    }

    // Verifies malformed product payloads receive the common validation error response before they reach persistence.
    @Test
    void productVariantValidationRejectsMissingFamilyId() throws Exception {
        HttpResponse<String> response = post("/api/products/variants", """
                {"name":"Invalid variant"}
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("productFamilyId");
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), true);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, boolean withToken) throws Exception {
        if (withToken) {
            request.header("Authorization", "Bearer test-token");
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isIn(200, 201);
        return objectMapper.readTree(response.body());
    }
}
