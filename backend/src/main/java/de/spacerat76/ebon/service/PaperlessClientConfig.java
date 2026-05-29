package de.spacerat76.ebon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.spacerat76.ebon.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PaperlessClientConfig {

    @Bean
    public PaperlessClient paperlessClient(RestTemplate restTemplate, AppProperties props, ObjectMapper objectMapper) {
        if (StringUtils.hasText(props.getPaperlessBaseUrl())) {
            return new PaperlessClientHttp(restTemplate, props, objectMapper);
        }
        return new NoOpPaperlessClient();
    }
}
