package de.spacerat76.ebon.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.spacerat76.ebon.config.AppProperties;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class OpenRouterAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterAiClient.class);

    private final RestTemplate rt;
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    // using manual retry/backoff loop to keep behavior explicit in tests

    public OpenRouterAiClient(RestTemplate rt, AppProperties props, ObjectMapper objectMapper) {
        this.rt = rt;
        this.props = props;
        this.objectMapper = objectMapper;

        // nothing to initialize for manual retry/backoff
    }

    @Override
    public Optional<AiParseResult> parseReceipt(String rawText) {
        String base = props.getOpenrouterBaseUrl();
        if (!StringUtils.hasText(base) || !StringUtils.hasText(props.getOpenrouterApiKey()) || !StringUtils.hasText(props.getOpenrouterModel())) {
            log.debug("OpenRouter not configured (base/key/model)");
            return Optional.empty();
        }

        try {
            String url = base + (base.endsWith("/") ? "" : "/") + "v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getOpenrouterApiKey());

            String systemPrompt = "You are a JSON-only extractor. Given an OCR'd receipt text, reply with valid JSON only (no explanations) containing these fields when available: storeName (string), totalAmount (number), receiptDate (YYYY-MM-DD), items (array of {description, quantity, unit, unitPrice, total}), currency (string), suggestedParseRegex (string). Omit fields you cannot determine.";

            Map<String, Object> body = Map.of(
                    "model", props.getOpenrouterModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", rawText)
                    )
            );

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = null;
            int attempts = 0;
            long waitMs = props.getAiRetryInitialWaitMs();
            while (attempts < props.getAiRetryMaxAttempts()) {
                attempts++;
                try {
                    resp = rt.postForEntity(url, req, String.class);
                    if (resp != null && resp.getStatusCode().value() == 429) {
                        String ra = resp.getHeaders().getFirst("Retry-After");
                        long raMs = parseRetryAfterToMs(ra, props.getAiRetryInitialWaitMs());
                        log.info("OpenRouter returned 429, attempt {} of {}, waiting {} ms before retry", attempts, props.getAiRetryMaxAttempts(), raMs);
                        Thread.sleep(raMs);
                        waitMs = Math.round(waitMs * props.getAiRetryBackoffMultiplier());
                        continue;
                    }
                    if (resp != null && resp.getStatusCode().is5xxServerError()) {
                        if (attempts >= props.getAiRetryMaxAttempts()) {
                            log.warn("OpenRouter server error after {} attempts: {}", attempts, resp.getStatusCode().value());
                            return Optional.empty();
                        }
                        log.info("OpenRouter server error ({}), attempt {}/{}. Backing off {} ms", resp.getStatusCode().value(), attempts, props.getAiRetryMaxAttempts(), waitMs);
                        Thread.sleep(waitMs);
                        waitMs = Math.round(waitMs * props.getAiRetryBackoffMultiplier());
                        continue;
                    }
                    break;
                } catch (Exception ex) {
                    if (attempts >= props.getAiRetryMaxAttempts()) {
                        log.warn("OpenRouter parseReceipt failed after {} attempts", attempts, ex);
                        return Optional.empty();
                    }
                    log.info("OpenRouter request failed ({}). attempt {}/{}. Backing off {} ms", ex.getClass().getSimpleName(), attempts, props.getAiRetryMaxAttempts(), waitMs);
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    waitMs = Math.round(waitMs * props.getAiRetryBackoffMultiplier());
                }
            }

            if (resp == null || !resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("OpenRouter returned non-2xx or empty body when parsing receipt");
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(resp.getBody());

            // try to extract cost from common paths
            BigDecimal cost = null;
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                String[] keys = new String[]{"total_cost", "cost", "estimated_cost", "total_cost_usd"};
                for (String k : keys) {
                    if (usage.has(k)) {
                        JsonNode n = usage.get(k);
                        if (n.isNumber()) cost = n.decimalValue();
                        else if (n.isTextual()) {
                            try { cost = new BigDecimal(n.asText()); } catch (Exception e) { /* ignore */ }
                        }
                        if (cost != null) break;
                    }
                }
            }

            // extract assistant content
            String content = null;
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode choice = root.get("choices").get(0);
                JsonNode message = choice.path("message");
                if (message.has("content")) {
                    JsonNode contentNode = message.get("content");
                    if (contentNode.isTextual()) {
                        content = contentNode.asText();
                    } else {
                        // content might be a JSON object node
                        content = objectMapper.writeValueAsString(contentNode);
                    }
                } else if (choice.has("text")) {
                    content = choice.get("text").asText();
                }
            }

            if (content == null || content.isBlank()) {
                log.debug("OpenRouter returned no content to parse");
                return Optional.empty();
            }

            // Attempt to parse content as JSON; if wrapped in text, try to extract JSON substring
            String candidate = content.trim();
            AiParseResult result = null;
            try {
                if (candidate.startsWith("{") || candidate.startsWith("[")) {
                    result = objectMapper.readValue(candidate, AiParseResult.class);
                } else {
                    int s = candidate.indexOf('{');
                    int e = candidate.lastIndexOf('}');
                    if (s >= 0 && e > s) {
                        String sub = candidate.substring(s, e + 1);
                        result = objectMapper.readValue(sub, AiParseResult.class);
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to parse assistant content as JSON", ex);
            }

            if (result == null) return Optional.empty();
            if (cost != null) result.setCost(cost);
            return Optional.of(result);
        } catch (Exception ex) {
            log.warn("Error calling OpenRouter AI", ex);
            return Optional.empty();
        }
    }

    private long parseRetryAfterToMs(String headerValue, long defaultMs) {
        if (headerValue == null) return defaultMs;
        try {
            long secs = Long.parseLong(headerValue.trim());
            return Math.max(0, secs * 1000L);
        } catch (NumberFormatException nfe) {
            return defaultMs;
        }
    }
}
