package de.ebon.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class OpenRouterAiReceiptParsingClient implements AiReceiptParsingClient {

    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;

    OpenRouterAiReceiptParsingClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public AiReceiptParsingClientResponse parseReceipt(AiReceiptParsingPrompt prompt, AiParsingSettings settings) {
        RestClientException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                OpenRouterChatResponse response = restClient.post()
                        .uri(settings.openRouterBaseUrl().replaceAll("/+$", "") + "/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + settings.openRouterApiKey())
                        .body(requestBody(prompt, settings))
                        .retrieve()
                        .body(OpenRouterChatResponse.class);
                return new AiReceiptParsingClientResponse(
                        extractContent(response),
                        settings.model(),
                        response == null || response.usage() == null ? null : response.usage().prompt_tokens(),
                        response == null || response.usage() == null ? null : response.usage().completion_tokens(),
                        response == null || response.usage() == null ? null : response.usage().total_tokens());
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

    private Map<String, Object> requestBody(AiReceiptParsingPrompt prompt, AiParsingSettings settings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("max_tokens", settings.maxTokens());
        body.put("temperature", settings.temperature());
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", """
                                Du extrahierst deutsche Kassenbons in ein festes JSON-Schema.
                                Antworte ausschliesslich mit einem vollstaendigen JSON-Objekt.
                                Erfinde keine Positionen, Summen oder Datumswerte.
                                Liefere parseRuleSuggestions nur als Vorschlaege, niemals als aktive Regeln.
                                """),
                Map.of(
                        "role", "user",
                        "content", buildUserPrompt(prompt, settings))));
        return body;
    }

    private String buildUserPrompt(AiReceiptParsingPrompt prompt, AiParsingSettings settings) {
        return """
                Extrahiere diesen Bon.

                Textmodus: %s
                Mindestkonfidenz fuer automatische Uebernahme: %s

                Regelparser-Fehler:
                %s

                Regelparser-Teilparse:
                %s

                Bontext:
                %s

                JSON-Felder:
                receiptDate, receiptTime, storeName, storeBranch, totalAmount, currency, bonusBalance, bonusPoints,
                bonusType, overallConfidence, fieldConfidence, warnings, items[], parseRuleSuggestions[].
                items[].positionIndex muss bei 0 starten und fortlaufend sein.
                bonusBalance und bonusPoints sind nur in diesem Einkauf neu gesammelte Werte.
                """.formatted(
                prompt.textMode(),
                settings.minConfidence(),
                nullToEmpty(prompt.ruleParserError()),
                nullToEmpty(prompt.ruleParserPartial()),
                nullToEmpty(prompt.rawTextForModel()));
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

    private record OpenRouterChatResponse(List<OpenRouterChoice> choices, OpenRouterUsage usage) {
    }

    private record OpenRouterChoice(OpenRouterMessage message) {
    }

    private record OpenRouterMessage(String content) {
    }

    private record OpenRouterUsage(Integer prompt_tokens, Integer completion_tokens, Integer total_tokens) {
    }
}
