package de.ebon.api;

import de.ebon.persistence.model.AiCategorizationLog;
import de.ebon.persistence.model.AiCategorizationRejectionReason;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.paperless.PaperlessClient;
import de.ebon.paperless.PaperlessDocument;
import de.ebon.persistence.repository.AiCategorizationLogRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(ReceiptApiContractTests.FakePaperlessClientConfig.class)
@TestPropertySource(properties = {
        "app.security.api-token=test-token",
        "app.sync.scheduler.enabled=false"
})
class ReceiptApiContractTests extends PostgresIntegrationTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReceiptItemRepository receiptItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AiCategorizationLogRepository aiLogRepository;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("truncate ai_categorization_log, receipt_item, receipt restart identity cascade");
        jdbcTemplate.update("update app_settings set value = '0.900' where key = 'ai_categorization_min_confidence'");
        upsertSetting("paperless_api_token", "paperless-secret");
        upsertSetting("openrouter_api_key", "openrouter-secret");
    }

    // Verifies that rejected AI suggestions are exposed in a UI-friendly DTO instead of raw AI JSON.
    @Test
    void receiptDetailsExposeRejectedLowConfidenceAndUnknownCategorySuggestions() throws Exception {
        Category drogerie = category("Drogerie");
        Receipt receipt = receipt("dm", "Mehrdeutiger Artikel", "Unbekannte Spezialposition");
        ReceiptItem lowConfidence = items(receipt).getFirst();
        ReceiptItem unknownCategory = items(receipt).get(1);
        aiLogRepository.save(new AiCategorizationLog(
                lowConfidence,
                "prompt",
                "response",
                drogerie,
                "Drogerie",
                null,
                new BigDecimal("0.820"),
                AiCategorizationRejectionReason.LOW_CONFIDENCE,
                "fake-model"));
        aiLogRepository.save(new AiCategorizationLog(
                unknownCategory,
                "prompt",
                "response",
                null,
                "Nicht vorhandene Kategorie",
                null,
                new BigDecimal("0.990"),
                AiCategorizationRejectionReason.UNKNOWN_CATEGORY,
                "fake-model"));

        HttpResponse<String> response = sendGet("/api/receipts/" + receipt.getId());
        JsonNode items = objectMapper.readTree(response.body()).get("items");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode lowConfidenceSuggestion = items.get(0).get("aiSuggestion");
        assertThat(lowConfidenceSuggestion.get("categoryId").asLong()).isEqualTo(drogerie.getId());
        assertThat(lowConfidenceSuggestion.get("categoryName").asString()).isEqualTo("Drogerie");
        assertThat(new BigDecimal(lowConfidenceSuggestion.get("confidence").asString())).isEqualByComparingTo("0.820");
        assertThat(lowConfidenceSuggestion.get("rejectionReason").asString()).isEqualTo("LOW_CONFIDENCE");

        JsonNode unknownSuggestion = items.get(1).get("aiSuggestion");
        assertThat(unknownSuggestion.get("categoryId").toString()).isEqualTo("null");
        assertThat(unknownSuggestion.get("categoryName").asString()).isEqualTo("Nicht vorhandene Kategorie");
        assertThat(unknownSuggestion.get("rejectionReason").asString()).isEqualTo("UNKNOWN_CATEGORY");
    }

    // Verifies that accepted categories suppress stale AI suggestions so the UI shows only actionable hints.
    @Test
    void receiptDetailsDoNotExposeAiSuggestionForCategorizedItems() throws Exception {
        Category drogerie = category("Drogerie");
        Receipt receipt = receipt("dm", "Shampoo");
        ReceiptItem item = items(receipt).getFirst();
        item.assignCategory(drogerie, CategorySource.AI);
        receiptItemRepository.saveAndFlush(item);
        aiLogRepository.save(new AiCategorizationLog(
                item,
                "prompt",
                "response",
                drogerie,
                "Drogerie",
                null,
                new BigDecimal("0.700"),
                AiCategorizationRejectionReason.LOW_CONFIDENCE,
                "fake-model"));

        HttpResponse<String> response = sendGet("/api/receipts/" + receipt.getId());
        JsonNode itemBody = objectMapper.readTree(response.body()).get("items").get(0);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(itemBody.get("categoryId").asLong()).isEqualTo(drogerie.getId());
        assertThat(itemBody.get("categorySource").asString()).isEqualTo("AI");
        assertThat(itemBody.get("aiSuggestion").toString()).isEqualTo("null");
    }

    // Verifies that clearing a category is recorded as a manual user decision and protected from later bulk rules.
    @Test
    void patchItemWithExplicitNullCategoryClearsCategoryAsManualDecision() throws Exception {
        Category lebensmittel = category("Lebensmittel");
        Receipt receipt = receipt("REWE", "Bio Milch");
        ReceiptItem item = items(receipt).getFirst();
        item.assignCategory(lebensmittel, CategorySource.RULE);
        receiptItemRepository.saveAndFlush(item);

        HttpResponse<String> response = sendPatch(
                "/api/receipt-items/" + item.getId(),
                """
                        { "categoryId": null }
                        """);
        JsonNode body = objectMapper.readTree(response.body());
        ReceiptItem reloaded = receiptItemRepository.findById(item.getId()).orElseThrow();
        Receipt reloadedReceipt = receiptRepository.findById(receipt.getId()).orElseThrow();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("categoryId").toString()).isEqualTo("null");
        assertThat(body.get("categorySource").toString()).isEqualTo("null");
        assertThat(body.get("isManuallyEdited").asBoolean()).isTrue();
        assertThat(reloaded.getCategory()).isNull();
        assertThat(reloaded.getCategorySource()).isNull();
        assertThat(reloaded.isManuallyEdited()).isTrue();
        assertThat(reloadedReceipt.getParseStatus()).isEqualTo(ParseStatus.MANUALLY_EDITED);
    }

    // Verifies DTO validation: categorySource is only valid together with a concrete categoryId.
    @Test
    void patchItemRejectsCategorySourceWithoutCategoryId() throws Exception {
        Receipt receipt = receipt("REWE", "Bio Milch");
        ReceiptItem item = items(receipt).getFirst();

        HttpResponse<String> response = sendPatch(
                "/api/receipt-items/" + item.getId(),
                """
                        { "categorySource": "AI" }
                        """);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("message").asString()).contains("categorySource");
    }

    // Verifies that normal receipt lists do not show soft-deleted receipts unless requested.
    @Test
    void listReceiptsHidesDeletedReceiptsByDefault() throws Exception {
        Receipt active = receiptWithDate("Aktiv", LocalDate.of(2026, 5, 1), false);
        receiptWithDate("Gelöscht", LocalDate.of(2026, 5, 2), true);

        HttpResponse<String> response = sendGet("/api/receipts");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("totalElements").asLong()).isEqualTo(1);
        assertThat(body.get("content").size()).isEqualTo(1);
        assertThat(body.get("content").get(0).get("storeName").asString()).isEqualTo(active.getStoreName());
    }

    // Verifies the explicit includeDeleted flag for audit/admin-style receipt lists.
    @Test
    void listReceiptsIncludesDeletedReceiptsWhenRequested() throws Exception {
        receiptWithDate("Aktiv", LocalDate.of(2026, 5, 1), false);
        Receipt deleted = receiptWithDate("Gelöscht", LocalDate.of(2026, 5, 2), true);

        HttpResponse<String> response = sendGet("/api/receipts?includeDeleted=true");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        assertThat(body.get("content").size()).isEqualTo(2);
        assertThat(body.get("content").get(0).get("storeName").asString()).isEqualTo(deleted.getStoreName());
        boolean hasDeletedEntry = false;
        for (JsonNode node : body.get("content")) {
            if (!node.get("deletedAt").isNull()) {
                hasDeletedEntry = true;
                break;
            }
        }
        assertThat(hasDeletedEntry).isTrue();
    }

    // Verifies receipt list filters and fallback sorting through the real HTTP contract.
    @Test
    void listReceiptsAppliesFiltersAndSafeSorting() throws Exception {
        receiptWithParseStatus("REWE Mitte", LocalDate.of(2026, 5, 1), ParseStatus.PARSED, false);
        receiptWithParseStatus("dm-drogerie markt", LocalDate.of(2026, 5, 2), ParseStatus.PARSE_ERROR, false);
        receiptWithParseStatus("REWE Süd", LocalDate.of(2026, 6, 1), ParseStatus.PARSED, true);

        HttpResponse<String> response = sendGet(
                "/api/receipts?status=PARSED&dateFrom=2026-05-01&dateTo=2026-05-31&store=rewe&sortBy=invalid&sortDir=desc&size=100");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("sortBy").asString()).isEqualTo("receiptDate");
        assertThat(body.get("sortDir").asString()).isEqualTo("desc");
        assertThat(body.get("size").asInt()).isEqualTo(100);
        assertThat(body.get("totalElements").asInt()).isEqualTo(1);
        assertThat(body.get("content").size()).isEqualTo(1);
        assertThat(body.get("content").get(0).get("storeName").asString()).isEqualTo("REWE Mitte");
    }

    // Verifies reparsing replaces old items safely without position-index conflicts in the database.
    @Test
    void reparseReceiptReplacesExistingItemsWithoutPositionIndexConflict() throws Exception {
        Receipt receipt = new Receipt(
                300001,
                """
                        REWE Markt
                        Am Reuschenberger Markt 1
                        27.05.2026 12:37
                        ALTE POSITION 0,50
                        ZWEITE POSITION 0,50
                        SUMME EUR 1,00
                        """);
        receipt.setStoreName("REWE");
        receipt.addItem(new ReceiptItem(0, "Vorherige Position A", new BigDecimal("0.50")));
        receipt.addItem(new ReceiptItem(1, "Vorherige Position B", new BigDecimal("0.50")));
        receiptRepository.saveAndFlush(receipt);

        HttpResponse<String> response = sendPost(
                "/api/receipts/" + receipt.getId() + "/reparse?overwriteManualEdits=false");
        JsonNode body = objectMapper.readTree(response.body());
        java.util.List<ReceiptItem> reparsedItems = items(receipt);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("storeBranch").asString()).isEqualTo("Am Reuschenberger Markt 1");
        assertThat(body.get("items")).hasSize(2);
        assertThat(reparsedItems)
                .extracting(ReceiptItem::getDescription)
                .containsExactly("ALTE POSITION", "ZWEITE POSITION");
    }

    // Verifies the reparse preflight is protected and returns only a safe change status, never raw text.
    @Test
    void paperlessRawTextStatusIsProtectedAndDoesNotExposeRawText() throws Exception {
        Receipt receipt = new Receipt(300002, "gespeicherter Rohtext");
        receipt.setStoreName("REWE");
        receiptRepository.saveAndFlush(receipt);

        HttpResponse<String> unauthorized = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/receipts/" + receipt.getId()
                                + "/paperless-raw-text-status"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> authorized = sendGet(
                "/api/receipts/" + receipt.getId() + "/paperless-raw-text-status");
        JsonNode body = objectMapper.readTree(authorized.body());

        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(authorized.statusCode()).isEqualTo(200);
        assertThat(body.get("status").asString()).isEqualTo("CHANGED");
        assertThat(body.has("rawText")).isFalse();
        assertThat(body.has("hash")).isFalse();
    }

    // Verifies only the explicit PAPERLESS reparse option replaces the persisted raw text.
    @Test
    void reparseWithPaperlessSourceUpdatesRawTextBeforeParsing() throws Exception {
        Receipt receipt = new Receipt(300003, "alter Rohtext");
        receipt.setStoreName("REWE");
        receiptRepository.saveAndFlush(receipt);

        HttpResponse<String> response = sendPost(
                "/api/receipts/" + receipt.getId() + "/reparse?rawTextSource=PAPERLESS");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "select raw_text from receipt where id = ?", String.class, receipt.getId()))
                .isEqualTo(FakePaperlessClientConfig.CURRENT_RAW_TEXT);
    }

    // Verifies settings responses mask secrets and updates never persist the mask placeholder as a secret.
    @Test
    void settingsMaskSecretsAndDoNotPersistMaskPlaceholder() throws Exception {
        HttpResponse<String> getResponse = sendGet("/api/settings");
        JsonNode settings = objectMapper.readTree(getResponse.body());

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(settings.get("paperlessApiToken").asString()).isEqualTo("********");
        assertThat(settings.get("openRouterApiKey").asString()).isEqualTo("********");
        assertThat(new BigDecimal(settings.get("aiCategorizationMinConfidence").asString())).isEqualByComparingTo("0.900");

        HttpResponse<String> putResponse = sendPut(
                "/api/settings",
                """
                        {
                          "paperlessApiToken": "********",
                          "openRouterApiKey": "********",
                          "aiCategorizationMinConfidence": 0.875
                        }
                        """);

        assertThat(putResponse.statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "select value from app_settings where key = 'paperless_api_token'",
                String.class)).isEqualTo("paperless-secret");
        assertThat(jdbcTemplate.queryForObject(
                "select value from app_settings where key = 'openrouter_api_key'",
                String.class)).isEqualTo("openrouter-secret");
        assertThat(jdbcTemplate.queryForObject(
                "select value from app_settings where key = 'ai_categorization_min_confidence'",
                String.class)).isEqualTo("0.875");
    }

    // Verifies API validation for the configurable AI categorization confidence range.
    @Test
    void settingsRejectInvalidAiCategorizationConfidence() throws Exception {
        HttpResponse<String> response = sendPut(
                "/api/settings",
                """
                        { "aiCategorizationMinConfidence": 1.500 }
                        """);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body.get("status").asInt()).isEqualTo(400);
    }

    private Receipt receipt(String storeName, String... descriptions) {
        Receipt receipt = new Receipt(200000 + Math.abs(String.join("|", descriptions).hashCode()), "raw text");
        receipt.setStoreName(storeName);
        for (int index = 0; index < descriptions.length; index++) {
            receipt.addItem(new ReceiptItem(index, descriptions[index], new BigDecimal("1.00")));
        }
        return receiptRepository.saveAndFlush(receipt);
    }

    private Receipt receiptWithDate(String storeName, LocalDate date, boolean deleted) {
        Receipt receipt = new Receipt(400000 + Math.abs(storeName.hashCode()) + date.getDayOfMonth(), "raw text");
        receipt.setStoreName(storeName);
        receiptRepository.saveAndFlush(receipt);
        receipt.updateManualValues(
                date,
                LocalTime.of(12, 0),
                storeName,
                null,
                new BigDecimal("1.00"),
                "EUR",
                null,
                null,
                null);
        if (deleted) {
            receipt.markDeleted(DeleteReason.USER_DELETED);
        }
        return receiptRepository.saveAndFlush(receipt);
    }

    private Receipt receiptWithParseStatus(
            String storeName,
            LocalDate date,
            ParseStatus parseStatus,
            boolean deleted) {
        Receipt receipt = new Receipt(500000 + Math.abs(storeName.hashCode()) + date.getDayOfMonth(), "raw text");
        receipt.applyParseResult(
                parseStatus,
                null,
                date,
                LocalTime.of(12, 0),
                storeName,
                null,
                new BigDecimal("1.00"),
                "EUR",
                null,
                null,
                null);
        if (deleted) {
            receipt.markDeleted(DeleteReason.USER_DELETED);
        }
        return receiptRepository.saveAndFlush(receipt);
    }

    private Category category(String name) {
        return categoryRepository.findByName(name).orElseThrow();
    }

    private void upsertSetting(String key, String value) {
        jdbcTemplate.update("""
                insert into app_settings (key, value, description)
                values (?, ?, ?)
                on conflict (key) do update set value = excluded.value
                """, key, value, "test setting");
    }

    private java.util.List<ReceiptItem> items(Receipt receipt) {
        return receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId());
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET());
    }

    private HttpResponse<String> sendPatch(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> sendPost(String path) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody()));
    }

    private HttpResponse<String> sendPut(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return httpClient.send(
                builder.header("Authorization", "Bearer test-token").build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class FakePaperlessClientConfig {

        static final String CURRENT_RAW_TEXT = """
                REWE
                19.06.2026
                Test Artikel 1,00
                SUMME EUR 1,00
                """;

        @Bean
        @Primary
        PaperlessClient paperlessClient() {
            return new PaperlessClient() {
                @Override
                public java.util.List<PaperlessDocument> fetchDocumentsByTag() {
                    return java.util.List.of();
                }

                @Override
                public PaperlessDocument fetchDocumentById(Integer documentId) {
                    return new PaperlessDocument(documentId, "Test document", "2026-06-19", CURRENT_RAW_TEXT);
                }
            };
        }
    }
}
