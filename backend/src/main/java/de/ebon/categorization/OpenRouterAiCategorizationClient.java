package de.ebon.categorization;

import de.ebon.config.AiCategorizationProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class OpenRouterAiCategorizationClient implements AiCategorizationClient {

    private static final int MAX_ATTEMPTS = 3;

    private final AiCategorizationProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    OpenRouterAiCategorizationClient(
            AiCategorizationProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getOpenrouterBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isAvailable() {
        return properties.hasApiKey();
    }

    @Override
    public AiCategorizationBatchResponse categorize(AiCategorizationBatchRequest request) {
        String prompt = buildPrompt(request);
        String content = requestCategorization(prompt);
        return new AiCategorizationBatchResponse(
                prompt,
                content,
                properties.getModel(),
                parseSuggestions(content));
    }

    private String requestCategorization(String prompt) {
        RestClientException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                OpenRouterChatResponse response = restClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getOpenrouterApiKey())
                        .body(requestBody(prompt))
                        .retrieve()
                        .body(OpenRouterChatResponse.class);
                return extractContent(response);
            } catch (RestClientException exception) {
                if (!isRetryable(exception) || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
                lastException = exception;
                backoff(attempt);
            }
        }
        throw lastException == null ? new IllegalStateException("OpenRouter-Antwort fehlt.") : lastException;
    }

    private Map<String, Object> requestBody(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("temperature", properties.getTemperature());
        body.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "Du kategorisierst Einkaufspositionen. Antworte ausschliesslich als JSON."),
                Map.of(
                        "role", "user",
                        "content", prompt)));
        return body;
    }

    private String buildPrompt(AiCategorizationBatchRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("Verfuegbare Kategorien: ")
                .append(String.join(", ", request.categoryNames()))
                .append("\n\n");
        builder.append("Kategorisiere diese Positionen. Antworte als JSON-Array mit itemId, category und confidence.\n");
        for (AiCategorizationItem item : request.items()) {
            builder.append("- itemId=")
                    .append(item.itemId())
                    .append("; Geschaeft=")
                    .append(nullToEmpty(item.storeName()))
                    .append("; Artikel=")
                    .append(nullToEmpty(item.description()))
                    .append('\n');
        }
        return builder.toString();
    }

    private List<AiCategorizationSuggestion> parseSuggestions(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode items = root.isArray() ? root : root.get("items");
            if (items == null) {
                return List.of();
            }

            List<AiCategorizationSuggestion> suggestions = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                JsonNode item = items.get(index);
                Long itemId = nullableLong(item, "itemId");
                String categoryName = nullableText(item, "category");
                if (categoryName == null) {
                    categoryName = nullableText(item, "categoryName");
                }
                suggestions.add(new AiCategorizationSuggestion(
                        itemId,
                        categoryName,
                        nullableDecimal(item, "confidence")));
            }
            return suggestions;
        } catch (RuntimeException exception) {
            return List.of();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String extractContent(OpenRouterChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return "";
        }
        OpenRouterChoice choice = response.choices().getFirst();
        return choice == null || choice.message() == null || choice.message().content() == null
                ? ""
                : choice.message().content();
    }

    private Long nullableLong(JsonNode node, String field) {
        String text = nullableText(node, field);
        return text == null ? null : Long.parseLong(text);
    }

    private BigDecimal nullableDecimal(JsonNode node, String field) {
        String text = nullableText(node, field);
        return text == null ? null : new BigDecimal(text);
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || "null".equals(value.toString())) {
            return null;
        }
        return value.asText();
    }

    private boolean isRetryable(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatusCode statusCode = responseException.getStatusCode();
            return statusCode.is5xxServerError();
        }
        return true;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep((long) Math.pow(2, attempt - 1) * 1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenRouter-Retry wurde unterbrochen.", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record OpenRouterChatResponse(List<OpenRouterChoice> choices) {
    }

    private record OpenRouterChoice(OpenRouterMessage message) {
    }

    private record OpenRouterMessage(String content) {
    }
}
