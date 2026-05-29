package de.spacerat76.ebon.ai;

import de.spacerat76.ebon.config.AppProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

public class OpenRouterAiClient implements AiClient {

    private final RestTemplate rt;
    private final AppProperties props;

    public OpenRouterAiClient(RestTemplate rt, AppProperties props) {
        this.rt = rt;
        this.props = props;
    }

    @Override
    public Optional<AiParseResult> parseReceipt(String rawText) {
        // Minimal scaffold: call OpenRouter / completions endpoint if configured.
        String base = props.getOpenrouterBaseUrl();
        if (base == null || props.getOpenrouterApiKey() == null) return Optional.empty();

        String url = base; // real implementation should append the correct path

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getOpenrouterApiKey());

        // Simple body scaffold — in a real implementation we would craft a prompt
        Map<String, Object> body = Map.of("input", rawText);

        try {
            HttpEntity<Map<String, Object>> e = new HttpEntity<>(body, headers);
            Map resp = rt.postForObject(url, e, Map.class);
            // Not parsing the response here — return empty to be safe
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
