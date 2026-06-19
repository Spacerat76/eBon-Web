package de.ebon.api;

import de.ebon.persistence.model.AiParsingLog;
import de.ebon.persistence.model.AiParsingStatus;
import de.ebon.persistence.model.AiParsingTrigger;
import de.ebon.persistence.model.ParseRuleSuggestion;
import de.ebon.persistence.model.ParseRuleType;
import de.ebon.persistence.model.ParseRuleValidationStatus;
import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.AiParsingLogRepository;
import de.ebon.persistence.repository.ParseRuleSuggestionRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class AiParsingApiContractTests extends PostgresIntegrationTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private AiParsingLogRepository aiParsingLogRepository;

    @Autowired
    private ParseRuleSuggestionRepository suggestionRepository;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("truncate parse_rule_suggestion, ai_parsing_log, receipt_item, receipt restart identity cascade");
    }

    // Verifies the list endpoint stays lightweight while the detail endpoint provides review context for the UI.
    @Test
    void ruleSuggestionDetailIncludesReceiptSourceAndParsedResult() throws Exception {
        ParseRuleSuggestion suggestion = parserSuggestion();

        HttpResponse<String> listResponse = sendGet("/api/parser/rule-suggestions?page=0&size=20");
        JsonNode listSuggestion = objectMapper.readTree(listResponse.body()).get("content").get(0);
        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listSuggestion.get("receiptContext").toString()).isEqualTo("null");

        HttpResponse<String> detailResponse = sendGet("/api/parser/rule-suggestions/" + suggestion.getId());
        JsonNode context = objectMapper.readTree(detailResponse.body()).get("receiptContext");

        assertThat(detailResponse.statusCode()).isEqualTo(200);
        assertThat(context.get("rawText").asString()).contains("Buesch GmbH", "Mehrkornbrot");
        assertThat(context.get("parseStatus").asString()).isEqualTo("PARSED");
        assertThat(context.get("parseSource").asString()).isEqualTo("AI");
        assertThat(context.get("storeName").asString()).isEqualTo("Buesch GmbH");
        assertThat(context.get("items").get(0).get("description").asString()).isEqualTo("Mehrkornbrot");
        assertThat(new BigDecimal(context.get("items").get(0).get("totalPrice").asString())).isEqualByComparingTo("4.20");
    }

    private ParseRuleSuggestion parserSuggestion() {
        Receipt receipt = new Receipt(1701, """
                Buesch GmbH
                1 Stk Mehrkornbrot 4,20 EUR
                Summe 4,20 EUR
                """);
        receipt.applyParseResult(
                ParseStatus.PARSED,
                null,
                LocalDate.of(2026, 6, 18),
                LocalTime.of(9, 15),
                "Buesch GmbH",
                "Neuss",
                new BigDecimal("4.20"),
                "EUR",
                null,
                null,
                null,
                ParseSource.AI);
        ReceiptItem item = new ReceiptItem(0, "Mehrkornbrot", new BigDecimal("4.20"));
        item.updateParsedValues(BigDecimal.ONE, "Stk", new BigDecimal("4.20"), null);
        receipt.addItem(item);
        receipt = receiptRepository.saveAndFlush(receipt);

        AiParsingLog log = new AiParsingLog(receipt, AiParsingTrigger.MANUAL_REPARSE, "Regelparser konnte Position nicht erkennen.");
        log.finish(
                AiParsingStatus.SUCCESS,
                "openai/gpt-oss-20b",
                null,
                new BigDecimal("0.950"),
                "{}",
                "[]",
                null,
                null,
                null,
                null,
                null);
        log = aiParsingLogRepository.saveAndFlush(log);

        return suggestionRepository.saveAndFlush(new ParseRuleSuggestion(
                log,
                receipt,
                "Buesch GmbH",
                ParseRuleType.ITEM_PATTERN,
                "^\\s*(\\d+)\\s+Stk\\s+(.*?)\\s+([\\d,.]+)\\s+EUR$",
                "quantity,description,total",
                new BigDecimal("0.920"),
                AiParsingTrigger.MANUAL_REPARSE,
                "Der Parser konnte die Positionszeile nicht erkennen.",
                "Die Regel erfasst Menge, Beschreibung und Gesamtpreis.",
                ParseRuleValidationStatus.VALID,
                null));
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("Authorization", "Bearer test-token")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
