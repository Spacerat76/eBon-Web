package de.spacerat76.ebon.config;

import de.spacerat76.ebon.ai.AiClient;
import de.spacerat76.ebon.ai.NoOpAiClient;
import de.spacerat76.ebon.ai.OpenRouterAiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiClientConfig {

    @Bean
    public AiClient aiClient(AppProperties props, org.springframework.web.client.RestTemplate restTemplate, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (props.getOpenrouterApiKey() == null || props.getOpenrouterApiKey().isBlank()) {
            return new NoOpAiClient();
        }
        return new OpenRouterAiClient(restTemplate, props, objectMapper);
    }
}
