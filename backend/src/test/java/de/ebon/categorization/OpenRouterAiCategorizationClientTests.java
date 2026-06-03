package de.ebon.categorization;

import de.ebon.config.AiCategorizationProperties;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterAiCategorizationClientTests {

    @Test
    void availabilityDependsOnApiKeyPresence() {
        AiCategorizationProperties properties = new AiCategorizationProperties();
        OpenRouterAiCategorizationClient client = new OpenRouterAiCategorizationClient(
                properties,
                RestClient.builder(),
                new ObjectMapper());

        assertThat(client.isAvailable()).isFalse();
        properties.setOpenrouterApiKey("secret");
        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    void buildPromptUsesCategoriesAndConfidenceAndNullSafeItemData() throws Exception {
        OpenRouterAiCategorizationClient client = client();
        AiCategorizationBatchRequest request = new AiCategorizationBatchRequest(
                List.of(
                        new AiCategorizationItem(1L, "Bio Milch", "REWE"),
                        new AiCategorizationItem(2L, null, null)),
                List.of("Lebensmittel", "Drogerie"),
                new BigDecimal("0.875"));

        String prompt = (String) invoke(client, "buildPrompt", new Class<?>[] {AiCategorizationBatchRequest.class}, request);

        assertThat(prompt).contains("Verfuegbare Kategorien: Lebensmittel, Drogerie");
        assertThat(prompt).contains("Mindest-Konfidenz 0.875");
        assertThat(prompt).contains("itemId=1; Geschaeft=REWE; Artikel=Bio Milch");
        assertThat(prompt).contains("itemId=2; Geschaeft=; Artikel=");
    }

    @Test
    void parseSuggestionsSupportsArrayObjectWrapperAndFallbackFields() throws Exception {
        OpenRouterAiCategorizationClient client = client();

        @SuppressWarnings("unchecked")
        List<AiCategorizationSuggestion> arraySuggestions = (List<AiCategorizationSuggestion>) invoke(
                client,
                "parseSuggestions",
                new Class<?>[] {String.class},
                """
                        [
                          {"itemId": "1", "category": "Drogerie", "confidence": "0.900"},
                          {"itemId": "2", "categoryName": "Lebensmittel", "confidence": "0.750"}
                        ]
                        """);
        assertThat(arraySuggestions).hasSize(2);
        assertThat(arraySuggestions.get(0).categoryName()).isEqualTo("Drogerie");
        assertThat(arraySuggestions.get(1).categoryName()).isEqualTo("Lebensmittel");

        @SuppressWarnings("unchecked")
        List<AiCategorizationSuggestion> wrappedSuggestions = (List<AiCategorizationSuggestion>) invoke(
                client,
                "parseSuggestions",
                new Class<?>[] {String.class},
                """
                        {
                          "items": [
                            {"itemId": 3, "category": null, "categoryName": "Gesundheit", "confidence": 0.5}
                          ]
                        }
                        """);
        assertThat(wrappedSuggestions).hasSize(1);
        assertThat(wrappedSuggestions.getFirst().categoryName()).isEqualTo("Gesundheit");

        @SuppressWarnings("unchecked")
        List<AiCategorizationSuggestion> invalidSuggestions = (List<AiCategorizationSuggestion>) invoke(
                client,
                "parseSuggestions",
                new Class<?>[] {String.class},
                "{ not-json }");
        assertThat(invalidSuggestions).isEmpty();
    }

    @Test
    void extractContentAndHelperMethodsHandleNullsAndStatusCodes() throws Exception {
        OpenRouterAiCategorizationClient client = client();

        assertThat((String) invoke(client, "extractContent", new Class<?>[] {openRouterResponseType()}, new Object[] {null}))
                .isEqualTo("");
        Object emptyChoices = openRouterResponse(List.of());
        assertThat((String) invoke(client, "extractContent", new Class<?>[] {openRouterResponseType()}, emptyChoices))
                .isEqualTo("");

        Object nullMessageChoice = openRouterChoice(null);
        Object nullMessageResponse = openRouterResponse(List.of(nullMessageChoice));
        assertThat((String) invoke(client, "extractContent", new Class<?>[] {openRouterResponseType()}, nullMessageResponse))
                .isEqualTo("");

        Object messageWithNullContent = openRouterResponse(List.of(openRouterChoice(openRouterMessage(null))));
        assertThat((String) invoke(client, "extractContent", new Class<?>[] {openRouterResponseType()}, messageWithNullContent))
                .isEqualTo("");

        Object response = openRouterResponse(List.of(openRouterChoice(openRouterMessage("payload"))));
        assertThat((String) invoke(client, "extractContent", new Class<?>[] {openRouterResponseType()}, response))
                .isEqualTo("payload");

        RestClientResponseException serverError = new RestClientResponseException(
                "server",
                500,
                "Internal Server Error",
                new HttpHeaders(),
                new byte[0],
                StandardCharsets.UTF_8);
        RestClientResponseException clientError = new RestClientResponseException(
                "client",
                400,
                "Bad Request",
                new HttpHeaders(),
                new byte[0],
                StandardCharsets.UTF_8);
        assertThat((boolean) invoke(client, "isRetryable", new Class<?>[] {RestClientException.class}, serverError))
                .isTrue();
        assertThat((boolean) invoke(client, "isRetryable", new Class<?>[] {RestClientException.class}, clientError))
                .isFalse();
        assertThat((String) invoke(client, "formatConfidence", new Class<?>[] {BigDecimal.class}, new BigDecimal("0.9")))
                .isEqualTo("0.900");
        assertThat((String) invoke(client, "formatConfidence", new Class<?>[] {BigDecimal.class}, new Object[] {null}))
                .isEqualTo("0.900");
    }

    private OpenRouterAiCategorizationClient client() {
        AiCategorizationProperties properties = new AiCategorizationProperties();
        properties.setOpenrouterApiKey("secret");
        return new OpenRouterAiCategorizationClient(properties, RestClient.builder(), new ObjectMapper());
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Class<?> openRouterResponseType() throws Exception {
        return Class.forName("de.ebon.categorization.OpenRouterAiCategorizationClient$OpenRouterChatResponse");
    }

    private Object openRouterResponse(List<?> choices) throws Exception {
        Constructor<?> constructor = openRouterResponseType().getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(choices);
    }

    private Object openRouterChoice(Object message) throws Exception {
        Class<?> type = Class.forName("de.ebon.categorization.OpenRouterAiCategorizationClient$OpenRouterChoice");
        Constructor<?> constructor = type.getDeclaredConstructor(Class.forName("de.ebon.categorization.OpenRouterAiCategorizationClient$OpenRouterMessage"));
        constructor.setAccessible(true);
        return constructor.newInstance(new Object[] {message});
    }

    private Object openRouterMessage(String content) throws Exception {
        Class<?> type = Class.forName("de.ebon.categorization.OpenRouterAiCategorizationClient$OpenRouterMessage");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(content);
    }
}
