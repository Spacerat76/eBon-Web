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
        HttpResponse<String> unconfirmedApply = post("/api/products/rules/" + rule.get("id").asLong() + "/apply", """
                {"confirm":false}
                """);
        assertThat(unconfirmedApply.statusCode()).isEqualTo(400);

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

    // Verifies invalid review pagination and confidence filters use the shared request-validation response.
    @Test
    void reviewQueueValidationRejectsOutOfRangeQueryParameters() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(uri("/api/products/review?page=-1&size=101&confidenceMax=1.001"))
                .GET(), true);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Validierungsfehler im Request.");
    }

    // Verifies the review queue exposes an auditable AI proposal and accepting it turns it into a manual confirmation.
    @Test
    void reviewQueueListsSuggestionWithPaginationAndAcceptsIt() throws Exception {
        String marker = "PHASE15REVIEW" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Long familyId = jdbcTemplate.queryForObject(
                "insert into product_family (name) values (?) returning id", Long.class, marker + " Haferdrink");
        Long variantId = jdbcTemplate.queryForObject("""
                insert into product_variant (product_family_id, name, total_quantity, total_unit)
                values (?, ?, 1.000, 'l')
                returning id
                """, Long.class, familyId, marker + " Haferdrink 1 l");
        Long receiptId = jdbcTemplate.queryForObject("""
                insert into receipt (paperless_document_id, receipt_date, store_name, total_amount, raw_text, parse_status)
                values (?, date '2026-06-22', 'dm', 1.79, 'contract test receipt', 'PARSED')
                returning id
                """, Long.class, marker.hashCode() & Integer.MAX_VALUE);
        Long itemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price,
                                          product_assignment_status, product_assignment_confidence)
                values (?, 0, ?, 1.79, 'NEEDS_REVIEW', 0.720)
                returning id
                """, Long.class, receiptId, marker + " Haferdrink");
        jdbcTemplate.update("""
                insert into product_assignment_log (
                    receipt_item_id, product_family_id, product_variant_id, source, status, confidence, model_used, decision_reason)
                values (?, ?, ?, 'AI', 'NEEDS_REVIEW', 0.720, 'mock-product-model', 'LOW_CONFIDENCE')
                """, itemId, familyId, variantId);

        HttpResponse<String> queueResponse = send(HttpRequest.newBuilder()
                .uri(uri("/api/products/review?page=0&size=10&store=dm&status=NEEDS_REVIEW"))
                .GET(), true);

        assertThat(queueResponse.statusCode()).isEqualTo(200);
        JsonNode queue = objectMapper.readTree(queueResponse.body());
        assertThat(queue.get("page").asInt()).isZero();
        JsonNode reviewItem = findContentItem(queue, itemId);
        assertThat(reviewItem).isNotNull();
        assertThat(reviewItem.get("suggestedProductVariantId").asLong()).isEqualTo(variantId);

        HttpResponse<String> accepted = post("/api/products/review/" + itemId + "/accept", "{}");

        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "select product_assignment_status from receipt_item where id = ?", String.class, itemId))
                .isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                "select product_assignment_source from receipt_item where id = ?", String.class, itemId))
                .isEqualTo("MANUAL");

        JsonNode suggestion = body(post("/api/products/review/" + itemId + "/rule-suggestion", """
                {"matchType":"EXACT","storeSpecific":true,"priority":20}
                """));
        assertThat(suggestion.get("rule").get("matchValue").asString()).isEqualTo(marker + " Haferdrink");
        assertThat(suggestion.get("preview").get("matchingItemsCount").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode acceptedRule = body(post("/api/products/review/" + itemId + "/rule-suggestion/accept", """
                {"rule":{"productFamilyId":%d,"productVariantId":%d,"storeName":"dm",
                 "matchType":"EXACT","matchValue":"%s Haferdrink","priority":20,"isActive":true},
                 "applyToExisting":false,"confirm":true}
                """.formatted(familyId, variantId, marker)));
        assertThat(acceptedRule.get("rule").get("productVariantId").asLong()).isEqualTo(variantId);
    }

    // Verifies a review correction can create a missing family inline and apply it to repeated open positions in the same store.
    @Test
    void reviewCorrectionCreatesFamilyAndAppliesToSameStoreDescription() throws Exception {
        String marker = "PHASE15INLINEFAMILY" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Long firstReceiptId = insertReceipt(marker, "REWE", 2.00);
        Long secondReceiptId = insertReceipt(marker + "SECOND", "REWE", 2.00);
        Long otherStoreReceiptId = insertReceipt(marker + "OTHER", "dm", 2.00);
        Long firstItemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price, product_assignment_status)
                values (?, 0, ?, 2.00, 'NEEDS_REVIEW') returning id
                """, Long.class, firstReceiptId, marker + " SERVICE GEW");
        Long secondItemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price, product_assignment_status)
                values (?, 0, ?, 2.00, 'NEEDS_REVIEW') returning id
                """, Long.class, secondReceiptId, marker + " SERVICE GEW");
        Long otherStoreItemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price, product_assignment_status)
                values (?, 0, ?, 2.00, 'NEEDS_REVIEW') returning id
                """, Long.class, otherStoreReceiptId, marker + " SERVICE GEW");

        JsonNode corrected = body(post("/api/products/review/" + firstItemId + "/correct", """
                {"newProductFamilyName":"%s Thekenware","productVariantId":null,"applyToSameStoreDescription":true}
                """.formatted(marker)));

        assertThat(corrected.get("currentProductFamilyName").asString()).isEqualTo(marker + " Thekenware");
        assertThat(corrected.get("possibleRetroactiveItems").asLong()).isEqualTo(2);
        Long familyId = jdbcTemplate.queryForObject(
                "select id from product_family where name = ?", Long.class, marker + " Thekenware");
        assertThat(jdbcTemplate.queryForObject(
                "select product_family_id from receipt_item where id = ?", Long.class, firstItemId))
                .isEqualTo(familyId);
        assertThat(jdbcTemplate.queryForObject(
                "select product_family_id from receipt_item where id = ?", Long.class, secondItemId))
                .isEqualTo(familyId);
        assertThat(jdbcTemplate.queryForObject(
                "select product_family_id from receipt_item where id = ?", Long.class, otherStoreItemId))
                .isNull();
    }

    // Verifies a historical family correction is previewed first and changes assignments only after explicit confirmation.
    @Test
    void familyMergePreviewDoesNotPersistAndConfirmedApplyMovesFamilyOnlyAssignments() throws Exception {
        String marker = "PHASE15MERGE" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Long sourceFamilyId = jdbcTemplate.queryForObject(
                "insert into product_family (name) values (?) returning id", Long.class, marker + " Source");
        Long targetFamilyId = jdbcTemplate.queryForObject(
                "insert into product_family (name) values (?) returning id", Long.class, marker + " Target");
        Long receiptId = jdbcTemplate.queryForObject("""
                insert into receipt (paperless_document_id, receipt_date, store_name, total_amount, raw_text, parse_status)
                values (?, date '2026-06-23', 'REWE', 2.49, 'contract test receipt', 'PARSED')
                returning id
                """, Long.class, marker.hashCode() & Integer.MAX_VALUE);
        Long itemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price,
                                          product_family_id, product_assignment_source, product_assignment_status)
                values (?, 0, ?, 2.49, ?, 'MANUAL', 'CONFIRMED')
                returning id
                """, Long.class, receiptId, marker + " Item", sourceFamilyId);

        JsonNode preview = body(post("/api/products/families/merge/preview", """
                {"sourceFamilyId":%d,"targetFamilyId":%d}
                """.formatted(sourceFamilyId, targetFamilyId)));
        assertThat(preview.get("affectedItemsCount").asLong()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select product_family_id from receipt_item where id = ?", Long.class, itemId))
                .isEqualTo(sourceFamilyId);

        JsonNode applied = body(post("/api/products/families/merge/apply", """
                {"sourceFamilyId":%d,"targetFamilyId":%d,"confirm":true}
                """.formatted(sourceFamilyId, targetFamilyId)));
        assertThat(applied.get("affectedItemsCount").asLong()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select product_family_id from receipt_item where id = ?", Long.class, itemId))
                .isEqualTo(targetFamilyId);
        assertThat(jdbcTemplate.queryForObject(
                "select is_active from product_family where id = ?", Boolean.class, sourceFamilyId))
                .isFalse();
    }

    // Verifies a variant merge has an immutable preview and records the confirmed historical correction.
    @Test
    void variantMergePreviewDoesNotPersistAndConfirmedApplyMovesAssignmentsAndRules() throws Exception {
        String marker = "PHASE15VARIANTMERGE" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Long familyId = jdbcTemplate.queryForObject(
                "insert into product_family (name) values (?) returning id", Long.class, marker + " Family");
        Long sourceVariantId = jdbcTemplate.queryForObject("""
                insert into product_variant (product_family_id, name) values (?, ?) returning id
                """, Long.class, familyId, marker + " Source");
        Long targetVariantId = jdbcTemplate.queryForObject("""
                insert into product_variant (product_family_id, name) values (?, ?) returning id
                """, Long.class, familyId, marker + " Target");
        Long receiptId = insertReceipt(marker, "REWE", 3.49);
        Long itemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price,
                                          product_family_id, product_variant_id, product_assignment_source, product_assignment_status)
                values (?, 0, ?, 3.49, ?, ?, 'MANUAL', 'CONFIRMED')
                returning id
                """, Long.class, receiptId, marker + " Item", familyId, sourceVariantId);
        jdbcTemplate.update("""
                insert into product_rule (product_family_id, product_variant_id, match_type, match_value, priority)
                values (?, ?, 'EXACT', ?, 10)
                """, familyId, sourceVariantId, marker + " Item");

        JsonNode preview = body(post("/api/products/variants/merge/preview", """
                {"sourceVariantId":%d,"targetVariantId":%d}
                """.formatted(sourceVariantId, targetVariantId)));
        assertThat(preview.get("affectedItemsCount").asLong()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select product_variant_id from receipt_item where id = ?", Long.class, itemId))
                .isEqualTo(sourceVariantId);

        body(post("/api/products/variants/merge/apply", """
                {"sourceVariantId":%d,"targetVariantId":%d,"confirm":true}
                """.formatted(sourceVariantId, targetVariantId)));

        assertThat(jdbcTemplate.queryForObject(
                "select product_variant_id from receipt_item where id = ?", Long.class, itemId))
                .isEqualTo(targetVariantId);
        assertThat(jdbcTemplate.queryForObject(
                "select product_variant_id from product_rule where match_value = ?", Long.class, marker + " Item"))
                .isEqualTo(targetVariantId);
        assertThat(jdbcTemplate.queryForObject(
                "select is_active from product_variant where id = ?", Boolean.class, sourceVariantId))
                .isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from product_assignment_log where receipt_item_id = ? and decision_reason = 'VARIANT_MERGE'",
                Long.class, itemId)).isEqualTo(1);
    }

    // Verifies a family split creates a new family only after confirmation and moves only the selected receipt positions.
    @Test
    void familySplitPreviewDoesNotPersistAndConfirmedApplyMovesOnlySelectedAssignments() throws Exception {
        String marker = "PHASE15FAMILYSPLIT" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Long sourceFamilyId = jdbcTemplate.queryForObject(
                "insert into product_family (name) values (?) returning id", Long.class, marker + " Source");
        Long receiptId = insertReceipt(marker, "dm", 4.00);
        Long selectedItemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price,
                                          product_family_id, product_assignment_source, product_assignment_status)
                values (?, 0, ?, 2.00, ?, 'MANUAL', 'CONFIRMED') returning id
                """, Long.class, receiptId, marker + " Selected", sourceFamilyId);
        Long untouchedItemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price,
                                          product_family_id, product_assignment_source, product_assignment_status)
                values (?, 1, ?, 2.00, ?, 'MANUAL', 'CONFIRMED') returning id
                """, Long.class, receiptId, marker + " Untouched", sourceFamilyId);

        String request = """
                {"sourceFamilyId":%d,"receiptItemIds":[%d],
                 "newFamily":{"name":"%s New","defaultCategoryId":null,"isActive":true}}
                """.formatted(sourceFamilyId, selectedItemId, marker);
        JsonNode preview = body(post("/api/products/families/split/preview", request));
        assertThat(preview.get("affectedItemsCount").asLong()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from product_family where name = ?", Long.class, marker + " New"))
                .isZero();

        String applyRequest = """
                {"sourceFamilyId":%d,"receiptItemIds":[%d],
                 "newFamily":{"name":"%s New","defaultCategoryId":null,"isActive":true},"confirm":true}
                """.formatted(sourceFamilyId, selectedItemId, marker);
        JsonNode applied = body(post("/api/products/families/split/apply", applyRequest));
        long newFamilyId = applied.get("newProductFamilyId").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "select product_family_id from receipt_item where id = ?", Long.class, selectedItemId))
                .isEqualTo(newFamilyId);
        assertThat(jdbcTemplate.queryForObject(
                "select product_family_id from receipt_item where id = ?", Long.class, untouchedItemId))
                .isEqualTo(sourceFamilyId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from product_assignment_log where receipt_item_id = ? and decision_reason = 'FAMILY_SPLIT'",
                Long.class, selectedItemId)).isEqualTo(1);
    }

    // Verifies a variant split creates a separate size/package variant only after confirmation.
    @Test
    void variantSplitPreviewDoesNotPersistAndConfirmedApplyMovesOnlySelectedAssignments() throws Exception {
        String marker = "PHASE15VARIANTSPLIT" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Long familyId = jdbcTemplate.queryForObject(
                "insert into product_family (name) values (?) returning id", Long.class, marker + " Family");
        Long sourceVariantId = jdbcTemplate.queryForObject("""
                insert into product_variant (product_family_id, name, total_quantity, total_unit)
                values (?, ?, 0.330, 'l') returning id
                """, Long.class, familyId, marker + " 0.33 l");
        Long receiptId = insertReceipt(marker, "REWE", 3.00);
        Long selectedItemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price,
                                          product_family_id, product_variant_id, product_assignment_source, product_assignment_status)
                values (?, 0, ?, 1.50, ?, ?, 'MANUAL', 'CONFIRMED') returning id
                """, Long.class, receiptId, marker + " Selected", familyId, sourceVariantId);
        Long untouchedItemId = jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price,
                                          product_family_id, product_variant_id, product_assignment_source, product_assignment_status)
                values (?, 1, ?, 1.50, ?, ?, 'MANUAL', 'CONFIRMED') returning id
                """, Long.class, receiptId, marker + " Untouched", familyId, sourceVariantId);

        String previewRequest = """
                {"sourceVariantId":%d,"receiptItemIds":[%d],
                 "newVariant":{"productFamilyId":%d,"name":"%s 0.50 l","totalQuantity":0.500,"totalUnit":"l","isActive":true}}
                """.formatted(sourceVariantId, selectedItemId, familyId, marker);
        JsonNode preview = body(post("/api/products/variants/split/preview", previewRequest));
        assertThat(preview.get("affectedItemsCount").asLong()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from product_variant where name = ?", Long.class, marker + " 0.50 l")).isZero();

        String applyRequest = """
                {"sourceVariantId":%d,"receiptItemIds":[%d],
                 "newVariant":{"productFamilyId":%d,"name":"%s 0.50 l","totalQuantity":0.500,"totalUnit":"l","isActive":true},"confirm":true}
                """.formatted(sourceVariantId, selectedItemId, familyId, marker);
        JsonNode applied = body(post("/api/products/variants/split/apply", applyRequest));
        long newVariantId = applied.get("newProductVariantId").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "select product_variant_id from receipt_item where id = ?", Long.class, selectedItemId))
                .isEqualTo(newVariantId);
        assertThat(jdbcTemplate.queryForObject(
                "select product_variant_id from receipt_item where id = ?", Long.class, untouchedItemId))
                .isEqualTo(sourceVariantId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from product_assignment_log where receipt_item_id = ? and decision_reason = 'VARIANT_SPLIT'",
                Long.class, selectedItemId)).isEqualTo(1);
    }

    // Verifies protected price reports expose normalized prices and keep manual exclusion reversible and auditable.
    @Test
    void productPriceEndpointsReportObservationsExcludeThemAndExportCsv() throws Exception {
        String marker = "PHASE15PRICE" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Long familyId = jdbcTemplate.queryForObject(
                "insert into product_family (name) values (?) returning id", Long.class, marker + " Haferdrink");
        Long variantId = jdbcTemplate.queryForObject("""
                insert into product_variant (product_family_id, name, total_quantity, total_unit)
                values (?, ?, 1.000, 'l') returning id
                """, Long.class, familyId, marker + " Haferdrink 1 l");

        Long firstItemId = insertPricedItem(marker, 1, familyId, variantId, "REWE", "Filiale A", "2026-01-10", "1.99");
        insertPricedItem(marker, 2, familyId, variantId, "REWE", "Filiale B", "2026-02-10", "2.49");
        insertPricedItem(marker, 3, familyId, variantId, "dm", "Innenstadt", "2026-03-10", "2.99");

        HttpResponse<String> unauthorized = send(HttpRequest.newBuilder()
                .uri(uri("/api/products/families/" + familyId + "/prices"))
                .GET(), false);
        assertThat(unauthorized.statusCode()).isEqualTo(401);

        JsonNode report = body(send(HttpRequest.newBuilder()
                .uri(uri("/api/products/families/" + familyId + "/prices?grouping=STORE_BRANCH"))
                .GET(), true));
        assertThat(report.get("primaryPriceBasis").asString()).isEqualTo("NORMALIZED_UNIT_PRICE");
        assertThat(report.get("statistics").get(0).get("observationCount").asLong()).isEqualTo(3);
        assertThat(report.get("stores")).hasSize(3);

        JsonNode excluded = body(post("/api/products/price-observations/" + firstItemId + "/exclude", """
                {"reason":"Duplikat aus Testdaten"}
                """));
        assertThat(excluded.get("excluded").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select exclude_from_product_price_comparison from receipt_item where id = ?", Boolean.class, firstItemId))
                .isTrue();

        HttpResponse<String> export = send(HttpRequest.newBuilder()
                .uri(uri("/api/products/families/" + familyId + "/prices/export"))
                .GET(), true);
        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(export.body()).startsWith("receiptItemId;receiptId;receiptDate;storeName");
        assertThat(export.body()).contains("excludedFromProductPriceComparison");

        JsonNode included = body(post("/api/products/price-observations/" + firstItemId + "/include", "{}"));
        assertThat(included.get("excluded").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from product_assignment_log where receipt_item_id = ? and decision_reason = 'PRICE_INCLUDED'",
                Long.class, firstItemId)).isEqualTo(1);
    }

    private Long insertReceipt(String marker, String storeName, double totalAmount) {
        return jdbcTemplate.queryForObject("""
                insert into receipt (paperless_document_id, receipt_date, store_name, total_amount, raw_text, parse_status)
                values (?, date '2026-06-23', ?, ?, 'contract test receipt', 'PARSED')
                returning id
                """, Long.class, marker.hashCode() & Integer.MAX_VALUE, storeName, totalAmount);
    }

    private Long insertPricedItem(
            String marker,
            int suffix,
            Long familyId,
            Long variantId,
            String storeName,
            String storeBranch,
            String receiptDate,
            String totalPrice) {
        Long receiptId = jdbcTemplate.queryForObject("""
                insert into receipt (paperless_document_id, receipt_date, store_name, store_branch, total_amount, raw_text, parse_status)
                values (?, cast(? as date), ?, ?, cast(? as numeric), 'contract test receipt', 'PARSED')
                returning id
                """, Long.class, (marker.hashCode() & Integer.MAX_VALUE) + suffix, receiptDate, storeName, storeBranch, totalPrice);
        return jdbcTemplate.queryForObject("""
                insert into receipt_item (receipt_id, position_index, description, total_price,
                                          product_family_id, product_variant_id, product_assignment_source, product_assignment_status)
                values (?, 0, ?, cast(? as numeric), ?, ?, 'MANUAL', 'CONFIRMED')
                returning id
                """, Long.class, receiptId, marker + " Haferdrink", totalPrice, familyId, variantId);
    }

    private JsonNode findContentItem(JsonNode page, Long receiptItemId) {
        for (JsonNode item : page.get("content")) {
            if (item.get("receiptItemId").asLong() == receiptItemId) {
                return item;
            }
        }
        return null;
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
